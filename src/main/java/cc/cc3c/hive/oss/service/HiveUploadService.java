package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveUploadCheckVO;
import cc.cc3c.hive.oss.thumbnail.ThumbnailAsyncService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Component
public class HiveUploadService {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveOssService hiveOssService;
    private final ThumbnailAsyncService thumbnailAsyncService;

    @Value("${hive.imageCacheDir}")
    private String imageCacheDir;

    public HiveUploadService(HiveRecordRepository hiveRecordRepository,
                             HiveOssService hiveOssService,
                             ThumbnailAsyncService thumbnailAsyncService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveOssService = hiveOssService;
        this.thumbnailAsyncService = thumbnailAsyncService;
    }

    @PostConstruct
    public void init() throws IOException {
        FileUtils.forceMkdir(new File(imageCacheDir));
    }

    /**
     * Upload file to OSS.
     * Images (uiVariant == "image" with a supported format) are written directly to imageCacheDir
     * under a stable name, uploaded to OSS, then handed to ThumbnailAsyncService without a copy.
     * All other files are streamed directly without local buffering.
     */
    public String uploadStreaming(String bucketName, CategoryStorageClass storageClass,
                                  String uiVariant, String fileName, InputStream inputStream) throws Exception {
        String fileKey = DigestUtils.md5Hex(fileName);
        CategoryStorageClass resolved = resolveStorageClass(storageClass);
        Optional<HiveRecord> existing = hiveRecordRepository.findByBucketNameAndFileKey(bucketName, fileKey);
        if (isActiveUploaded(existing)) {
            IOUtils.closeQuietly(inputStream);
            return fileKey;
        }
        // startUploadRecord is outside the try block: UploadAlreadyInProgressException propagates
        // without triggering markFailed because no save has occurred at that point.
        HiveRecord hiveRecord = startUploadRecord(existing.orElseGet(HiveRecord::new), bucketName, fileName, fileKey);
        try {
            boolean thumbnail = thumbnailAsyncService.isThumbnailEligible(uiVariant, fileName);
            if (thumbnail) {
                doBufferedUpload(hiveRecord, fileKey, fileName, resolved, true, inputStream);
            } else {
                doStreamUpload(hiveRecord, fileKey, fileName, resolved, inputStream);
            }
            return fileKey;
        } catch (Exception e) {
            markFailed(hiveRecord);
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

    private void doBufferedUpload(HiveRecord hiveRecord, String fileKey, String fileName,
                                   CategoryStorageClass storageClass, boolean generateThumbnail,
                                   InputStream inputStream) throws Exception {
        File cacheFile = new File(imageCacheDir, fileKey + "_src");
        try {
            try (InputStream in = inputStream; FileOutputStream out = new FileOutputStream(cacheFile)) {
                IOUtils.copy(in, out);
            }
            try (InputStream fileIn = new FileInputStream(cacheFile)) {
                HiveOssTask task = HiveOssTask.createTask()
                        .withBucket(hiveRecord.getBucketName())
                        .withKey(fileKey)
                        .withInputStream(fileIn)
                        .withStorageClass(storageClass.name())
                        .withEncryption(fileName);
                hiveOssService.using(hiveRecord.getProvider()).uploadSync(task);
            }
            markUploaded(hiveRecord, storageClass);
            if (generateThumbnail) {
                thumbnailAsyncService.submitAsync(hiveRecord, fileKey);
            }
        } catch (Exception e) {
            FileUtils.deleteQuietly(cacheFile);
            throw e;
        }
    }

    private void doStreamUpload(HiveRecord hiveRecord, String fileKey, String fileName,
                                 CategoryStorageClass storageClass, InputStream inputStream) throws Exception {
        HiveOssTask task = HiveOssTask.createTask()
                .withBucket(hiveRecord.getBucketName())
                .withKey(fileKey)
                .withInputStream(inputStream)
                .withStorageClass(storageClass.name())
                .withEncryption(fileName);
        hiveOssService.using(hiveRecord.getProvider()).uploadStreaming(task);
        markUploaded(hiveRecord, storageClass);
    }

    private void markUploaded(HiveRecord hiveRecord, CategoryStorageClass storageClass) {
        hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
        hiveRecord.setDeletable(isDeletableAfterUpload(storageClass));
        hiveRecordRepository.save(hiveRecord);
    }

    private void markFailed(HiveRecord hiveRecord) {
        hiveRecord.setStatus(HiveRecordStatus.FAILED);
        hiveRecordRepository.save(hiveRecord);
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

    private boolean isDeletableAfterUpload(CategoryStorageClass storageClass) {
        return resolveStorageClass(storageClass) != CategoryStorageClass.ARCHIVE;
    }
}
