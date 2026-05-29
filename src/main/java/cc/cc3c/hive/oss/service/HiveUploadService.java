package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
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
    private final FileCategoryRepository fileCategoryRepository;
    private final HiveOssService hiveOssService;
    private final ThumbnailAsyncService thumbnailAsyncService;
    private final HiveRecordStateService stateService;

    @Value("${hive.imageCacheDir}")
    private String imageCacheDir;

    public HiveUploadService(HiveRecordRepository hiveRecordRepository,
                              FileCategoryRepository fileCategoryRepository,
                              HiveOssService hiveOssService,
                              ThumbnailAsyncService thumbnailAsyncService,
                              HiveRecordStateService stateService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileCategoryRepository = fileCategoryRepository;
        this.hiveOssService = hiveOssService;
        this.thumbnailAsyncService = thumbnailAsyncService;
        this.stateService = stateService;
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
    public String uploadStreaming(Long categoryId, CategoryStorageClass storageClass,
                                   String uiVariant, String fileName, InputStream inputStream) throws Exception {
        FileCategoryEntity category = fileCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryId));
        String bucketName = category.getBucketName();
        String fileKey = DigestUtils.md5Hex(fileName);
        CategoryStorageClass resolved = resolveStorageClass(storageClass);
        Optional<HiveRecord> existing = hiveRecordRepository.findByCategory_IdAndFileKeyAndDeletedIsFalse(categoryId, fileKey);
        if (isActiveUploaded(existing)) {
            IOUtils.closeQuietly(inputStream);
            return fileKey;
        }
        // startUploadRecord is outside the upload try block: rejected state transitions propagate
        // without triggering markFailed because no save has occurred at that point.
        HiveRecord hiveRecord;
        try {
            hiveRecord = startUploadRecord(existing.orElseGet(HiveRecord::new), category, fileName, fileKey);
        } catch (RuntimeException e) {
            IOUtils.closeQuietly(inputStream);
            throw e;
        }
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

    public boolean canUpload(Long categoryId, String fileName) {
        String fileKey = DigestUtils.md5Hex(fileName);
        Optional<HiveRecord> existing = hiveRecordRepository.findByCategory_IdAndFileKeyAndDeletedIsFalse(categoryId, fileKey);
        if (existing.isEmpty()) {
            return true;
        }
        return HiveRecordStatus.FAILED.equals(existing.get().getStatus());
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
                        .withBucket(hiveRecord.getCategory().getBucketName())
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
                .withBucket(hiveRecord.getCategory().getBucketName())
                .withKey(fileKey)
                .withInputStream(inputStream)
                .withStorageClass(storageClass.name())
                .withEncryption(fileName);
        hiveOssService.using(hiveRecord.getProvider()).uploadStreaming(task);
        markUploaded(hiveRecord, storageClass);
    }

    private void markUploaded(HiveRecord hiveRecord, CategoryStorageClass storageClass) {
        stateService.markUploaded(hiveRecord, storageClass);
        hiveRecordRepository.save(hiveRecord);
    }

    private void markFailed(HiveRecord hiveRecord) {
        stateService.markUploadFailed(hiveRecord);
        hiveRecordRepository.save(hiveRecord);
    }

    private HiveRecord startUploadRecord(HiveRecord hiveRecord, FileCategoryEntity category, String fileName, String fileKey) {
        stateService.startUpload(hiveRecord, category, fileName, fileKey);
        return hiveRecordRepository.save(hiveRecord);
    }

    private boolean isActiveUploaded(Optional<HiveRecord> existing) {
        return existing.isPresent()
                && HiveRecordStatus.UPLOADED.equals(existing.get().getStatus());
    }

    private CategoryStorageClass resolveStorageClass(CategoryStorageClass storageClass) {
        return storageClass == null ? CategoryStorageClass.STANDARD : storageClass;
    }
}
