package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.thumbnail.ThumbnailAsyncService;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
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
            long originalSize;
            if (thumbnail) {
                originalSize = doBufferedUpload(hiveRecord, fileKey, fileName, resolved, inputStream);
            } else {
                originalSize = doStreamUpload(hiveRecord, fileKey, fileName, resolved, inputStream);
            }
            hiveRecord.setOriginalSize(originalSize);
            refreshUploadedRecord(hiveRecord, resolved);
            if (thumbnail) {
                thumbnailAsyncService.submitAsync(hiveRecord, fileKey);
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

    private long doBufferedUpload(HiveRecord hiveRecord, String fileKey, String fileName,
                                  CategoryStorageClass storageClass, InputStream inputStream) throws Exception {
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
            return cacheFile.length();
        } catch (Exception e) {
            FileUtils.deleteQuietly(cacheFile);
            throw e;
        }
    }

    private long doStreamUpload(HiveRecord hiveRecord, String fileKey, String fileName,
                                CategoryStorageClass storageClass, InputStream inputStream) throws Exception {
        CountingInputStream countingInput = new CountingInputStream(inputStream);
        HiveOssTask task = HiveOssTask.createTask()
                .withBucket(hiveRecord.getCategory().getBucketName())
                .withKey(fileKey)
                .withInputStream(countingInput)
                .withStorageClass(storageClass.name())
                .withEncryption(fileName);
        hiveOssService.using(hiveRecord.getProvider()).uploadStreaming(task);
        return countingInput.getCount();
    }

    private void refreshUploadedRecord(HiveRecord hiveRecord, CategoryStorageClass storageClass) {
        try {
            HiveOssTask metadataTask = HiveOssTask.createTask()
                    .withBucket(hiveRecord.getCategory().getBucketName())
                    .withKey(hiveRecord.getFileKey());
            HiveOssObject metadata = hiveOssService.using(hiveRecord.getProvider()).getObjectMetadata(metadataTask);
            if (metadata == null || metadata.getLastModified() == null) {
                throw new IllegalStateException("uploaded object metadata is incomplete");
            }
            stateService.markUploaded(hiveRecord, hiveRecord.getCategory(), metadata, storageClass);
            hiveRecordRepository.save(hiveRecord);
        } catch (Exception e) {
            log.warn("refresh uploaded object metadata failed, fileKey={}", hiveRecord.getFileKey(), e);
            stateService.markUploaded(hiveRecord, storageClass);
            hiveRecordRepository.save(hiveRecord);
        }
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

    private static class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        private long getCount() {
            return count;
        }
    }

}
