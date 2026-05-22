package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveUploadCheckVO;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

@Slf4j
@Component
public class HiveUploadService implements FileAlterationListener {

    private static final String LEGACY_ARCHIVE_FOLDER_NAME = "ALIBABA_ACHIEVE";
    private static final String CATEGORY_IMAGE_PREVIEW = "IMAGE_PREVIEW";

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final HiveOssService hiveOssService;
    private final FileCategoryResolver fileCategoryResolver;
    private final ThumbnailGeneratorService thumbnailGeneratorService;

    public HiveUploadService(HiveRecordRepository hiveRecordRepository,
                             HiveRecordImageMetaRepository imageMetaRepository,
                             HiveOssService hiveOssService,
                             FileCategoryResolver fileCategoryResolver,
                             ThumbnailGeneratorService thumbnailGeneratorService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.hiveOssService = hiveOssService;
        this.fileCategoryResolver = fileCategoryResolver;
        this.thumbnailGeneratorService = thumbnailGeneratorService;
    }

    @Getter
    private File legacyStandardFolder;
    @Getter
    private File legacyArchiveFolder;

    private String uploadDir;

    @Value("${hive.uploadDir}")
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @PostConstruct
    public void init() {
        // Legacy compatibility: watcher still consumes the historical folder names.
        legacyStandardFolder = new File(uploadDir + "ALIBABA_STANDARD");
        legacyArchiveFolder = new File(uploadDir + LEGACY_ARCHIVE_FOLDER_NAME);
        try {
            FileUtils.forceMkdir(legacyStandardFolder);
            FileUtils.forceMkdir(legacyArchiveFolder);
        } catch (IOException e) {
            log.error("fail to create upload folder", e);
            System.exit(1);
        }
    }

    @Override
    public void onStart(FileAlterationObserver fileAlterationObserver) {

    }

    @Override
    public void onDirectoryCreate(File file) {
    }

    @Override
    public void onDirectoryChange(File file) {

    }

    @Override
    public void onDirectoryDelete(File file) {

    }

    @Override
    public void onFileCreate(File file) {
        HiveRecord hiveRecord = null;
        try {
            String fileName = file.getName();
            String fileKey = DigestUtils.md5Hex(file.getName());
            CategoryStorageClass storageClass = file.getCanonicalPath().contains(LEGACY_ARCHIVE_FOLDER_NAME)
                    ? CategoryStorageClass.ARCHIVE
                    : CategoryStorageClass.STANDARD;
            log.warn("legacy watcher folder semantics in use; path={}, inferredStorageClass={}", file.getCanonicalPath(), storageClass);
            String bucketName = fileCategoryResolver.resolveDefaultCategoryByStorageClass(storageClass).getBucketName();
            Optional<HiveRecord> existing = hiveRecordRepository.findByBucketNameAndFileKey(bucketName, fileKey);
            if (isActiveUploaded(existing)) {
                FileUtils.deleteQuietly(file);
                return;
            }
            hiveRecord = startUploadRecord(existing.orElseGet(HiveRecord::new), bucketName, fileName, fileKey);
            try (InputStream fileIn = new FileInputStream(file)) {
                HiveOssTask task = HiveOssTask.createTask()
                        .withBucket(bucketName)
                        .withKey(fileKey)
                        .withInputStream(fileIn)
                        .withStorageClass(storageClass.name())
                        .withEncryption(fileName);
                hiveOssService.using(hiveRecord.getProvider()).upload(task);
            }

            hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
            hiveRecordRepository.save(hiveRecord);
            FileUtils.deleteQuietly(file);
        } catch (Exception e) {
            if (hiveRecord != null && hiveRecord.getId() != null) {
                hiveRecord.setStatus(HiveRecordStatus.FAILED);
                hiveRecordRepository.save(hiveRecord);
            }
            log.error("onFileCreate", e);
        }
    }

    @Override
    public void onFileChange(File file) {

    }

    @Override
    public void onFileDelete(File file) {

    }

    @Override
    public void onStop(FileAlterationObserver fileAlterationObserver) {

    }

    /**
     * Upload file and optionally generate+upload encrypted thumbnail for IMAGE_PREVIEW.
     *
     * @param categoryCode category code (e.g. IMAGE_PREVIEW); used to decide whether to generate thumbnail
     */
    public String uploadSync(String bucketName, CategoryStorageClass storageClass, String categoryCode, String fileName, InputStream inputStream) throws Exception {
        String fileKey = DigestUtils.md5Hex(fileName);
        File tempFile = null;
        HiveRecord hiveRecord = null;
        CategoryStorageClass resolvedStorageClass = resolveStorageClass(storageClass);
        Optional<HiveRecord> existing = hiveRecordRepository.findByBucketNameAndFileKey(bucketName, fileKey);
        if (isActiveUploaded(existing)) {
            IOUtils.closeQuietly(inputStream);
            return fileKey;
        }
        try {
            tempFile = Files.createTempFile("hive-upload-", null).toFile();
            try (InputStream in = inputStream; java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                IOUtils.copy(in, out);
            }
            hiveRecord = startUploadRecord(existing.orElseGet(HiveRecord::new), bucketName, fileName, fileKey);
            try (InputStream fileIn = new FileInputStream(tempFile)) {
                HiveOssTask task = HiveOssTask.createTask()
                        .withBucket(bucketName)
                        .withKey(fileKey)
                        .withInputStream(fileIn)
                        .withStorageClass(resolvedStorageClass.name())
                        .withEncryption(fileName);

                hiveOssService.using(hiveRecord.getProvider()).uploadSync(task);

                hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
                hiveRecordRepository.save(hiveRecord);
                if (CATEGORY_IMAGE_PREVIEW.equals(StringUtils.trimToEmpty(categoryCode).toUpperCase())
                        && thumbnailGeneratorService.isSupportedImage(fileName)) {
                    uploadThumbnailAndSaveMeta(hiveRecord, fileKey, tempFile, resolvedStorageClass);
                }
                return fileKey;
            }
        } catch (Exception e) {
            if (hiveRecord != null && hiveRecord.getId() != null) {
                hiveRecord.setStatus(HiveRecordStatus.FAILED);
                hiveRecordRepository.save(hiveRecord);
            }
            throw e;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                FileUtils.deleteQuietly(tempFile);
            }
        }
    }

    /**
     * Stream the request body directly to OSS multipart upload with encryption.
     * The source file is not materialized on local disk; IMAGE_PREVIEW thumbnails are skipped on this path.
     */
    public String uploadStreaming(String bucketName, CategoryStorageClass storageClass, String categoryCode, String fileName, InputStream inputStream) throws Exception {
        String fileKey = DigestUtils.md5Hex(fileName);
        CategoryStorageClass resolvedStorageClass = resolveStorageClass(storageClass);
        Optional<HiveRecord> existing = hiveRecordRepository.findByBucketNameAndFileKey(bucketName, fileKey);
        if (isActiveUploaded(existing)) {
            IOUtils.closeQuietly(inputStream);
            return fileKey;
        }
        HiveRecord hiveRecord = startUploadRecord(existing.orElseGet(HiveRecord::new), bucketName, fileName, fileKey);
        try {
            HiveOssTask task = HiveOssTask.createTask()
                    .withBucket(bucketName)
                    .withKey(fileKey)
                    .withInputStream(inputStream)
                    .withStorageClass(resolvedStorageClass.name())
                    .withEncryption(fileName);

            hiveOssService.using(hiveRecord.getProvider()).uploadStreaming(task);

            hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
            hiveRecordRepository.save(hiveRecord);
            if (CATEGORY_IMAGE_PREVIEW.equals(StringUtils.trimToEmpty(categoryCode).toUpperCase())
                    && thumbnailGeneratorService.isSupportedImage(fileName)) {
                log.info("skip thumbnail generation for streaming upload, fileKey={}", fileKey);
            }
            return fileKey;
        } catch (Exception e) {
            if (hiveRecord.getId() != null) {
                hiveRecord.setStatus(HiveRecordStatus.FAILED);
                hiveRecordRepository.save(hiveRecord);
            }
            throw e;
        }
    }

    public HiveUploadCheckVO checkUpload(String bucketName, String fileName) {
        String fileKey = DigestUtils.md5Hex(fileName);
        Optional<HiveRecord> existing = hiveRecordRepository.findByBucketNameAndFileKey(bucketName, fileKey);
        if (existing.isEmpty() || Boolean.TRUE.equals(existing.get().getDeleted())) {
            return HiveUploadCheckVO.builder()
                    .exists(false)
                    .fileKey(fileKey)
                    .status(null)
                    .build();
        }

        HiveRecord record = existing.get();
        return HiveUploadCheckVO.builder()
                .exists(HiveRecordStatus.UPLOADED.equals(record.getStatus()))
                .fileKey(fileKey)
                .status(record.getStatus())
                .build();
    }

    private HiveRecord startUploadRecord(HiveRecord hiveRecord, String bucketName, String fileName, String fileKey) {
        if (hiveRecord.getId() != null
                && !Boolean.TRUE.equals(hiveRecord.getDeleted())
                && HiveRecordStatus.UPLOADING.equals(hiveRecord.getStatus())) {
            throw new UploadAlreadyInProgressException(fileName);
        }
        hiveRecord.setFileName(fileName);
        hiveRecord.setFileKey(fileKey);
        hiveRecord.setZipped(false);
        hiveRecord.setProvider(HiveStorageProvider.ALIBABA);
        hiveRecord.setBucketName(bucketName);
        hiveRecord.setStatus(HiveRecordStatus.UPLOADING);
        hiveRecord.setDeleted(false);
        hiveRecord.setDeletable(false);
        hiveRecord.setDownloadStatus(null);
        return hiveRecordRepository.save(hiveRecord);
    }

    private boolean isActiveUploaded(Optional<HiveRecord> existing) {
        return existing.isPresent()
                && !Boolean.TRUE.equals(existing.get().getDeleted())
                && HiveRecordStatus.UPLOADED.equals(existing.get().getStatus());
    }

    private CategoryStorageClass resolveStorageClass(CategoryStorageClass storageClass) {
        return storageClass == null ? CategoryStorageClass.STANDARD : storageClass;
    }

    /**
     * Generate thumbnail, upload with encryption, and persist thumbKey/thumbStatus/dimensions in hive_record_image_meta.
     * On any failure sets thumbStatus to FAILED and does not throw.
     */
    private void uploadThumbnailAndSaveMeta(HiveRecord hiveRecord, String fileKey, File imageFile, CategoryStorageClass storageClass) {
        LocalDateTime now = LocalDateTime.now();
        HiveRecordImageMeta meta = HiveRecordImageMeta.builder()
                .hiveRecordId(hiveRecord.getId())
                .thumbStatus("PENDING")
                .createdAt(now)
                .updatedAt(now)
                .build();
        imageMetaRepository.save(meta);
        try {
            var thumbOpt = thumbnailGeneratorService.generateFromFile(imageFile);
            if (thumbOpt.isEmpty()) {
                meta.setThumbStatus("FAILED");
                meta.setUpdatedAt(LocalDateTime.now());
                imageMetaRepository.save(meta);
                return;
            }
            var thumb = thumbOpt.get();
            String thumbKey = ThumbnailKeyHelper.thumbKey(fileKey);
            String thumbFileName = ThumbnailKeyHelper.thumbFileNameForEncryption(fileKey);
            HiveOssTask thumbTask = HiveOssTask.createTask()
                    .withBucket(hiveRecord.getBucketName())
                    .withKey(thumbKey)
                    .withInputStream(new ByteArrayInputStream(thumb.thumbJpeg()))
                    .withStorageClass(resolveStorageClass(storageClass).name())
                    .withEncryption(thumbFileName);
            hiveOssService.using(hiveRecord.getProvider()).uploadSync(thumbTask);
            meta.setThumbKey(thumbKey);
            meta.setThumbStatus("READY");
            meta.setImageWidth(thumb.imageWidth());
            meta.setImageHeight(thumb.imageHeight());
            meta.setUpdatedAt(LocalDateTime.now());
            imageMetaRepository.save(meta);
        } catch (Exception e) {
            log.warn("thumbnail upload failed for fileKey={}", fileKey, e);
            meta.setThumbStatus("FAILED");
            meta.setUpdatedAt(LocalDateTime.now());
            imageMetaRepository.save(meta);
        }
    }
}
