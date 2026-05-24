package cc.cc3c.hive.oss.thumbnail;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asynchronous thumbnail generation service.
 *
 * Upload flow: main file is written directly to imageCacheDir as {@code <fileKey>_src} by
 * HiveUploadService, then submitAsync() saves PENDING meta and returns immediately. A virtual
 * thread generates the thumbnail, uploads it, and updates the meta to READY or FAILED.
 * The cache file is retained after generation and evicted by the nightly TTL cleanup.
 *
 * On startup, any PENDING meta records are recovered: if the cache file is present the task
 * is resubmitted; if it is missing (process crash) the meta is marked FAILED.
 */
@Slf4j
@Component
public class ThumbnailAsyncService {

    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final HiveRecordRepository hiveRecordRepository;
    private final HiveOssService hiveOssService;
    private final ThumbnailGeneratorService thumbnailGeneratorService;

    @Value("${hive.imageCacheDir}")
    private String imageCacheDir;

    @Value("${hive.imageCacheTtlDays:7}")
    private int imageCacheTtlDays;

    public ThumbnailAsyncService(HiveRecordImageMetaRepository imageMetaRepository,
                                  HiveRecordRepository hiveRecordRepository,
                                  HiveOssService hiveOssService,
                                  ThumbnailGeneratorService thumbnailGeneratorService) {
        this.imageMetaRepository = imageMetaRepository;
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveOssService = hiveOssService;
        this.thumbnailGeneratorService = thumbnailGeneratorService;
    }

    @PostConstruct
    public void init() throws IOException {
        FileUtils.forceMkdir(new File(imageCacheDir));
        recoverPendingOnStartup();
    }

    /** Returns true if this file should have a thumbnail generated (image category + supported format). */
    public boolean isThumbnailEligible(String uiVariant, String fileName) {
        return "image".equalsIgnoreCase(uiVariant) && thumbnailGeneratorService.isSupportedImage(fileName);
    }

    /**
     * Saves PENDING meta and submits a virtual thread to generate and upload the thumbnail.
     * The source file must already exist at {@code imageCacheDir/<fileKey>_src} — written there
     * by HiveUploadService, so no copy is needed. Returns immediately after submitting.
     */
    public void submitAsync(HiveRecord hiveRecord, String fileKey) {
        File stagingFile = cacheFileFor(fileKey);

        LocalDateTime now = LocalDateTime.now();
        HiveRecordImageMeta meta = HiveRecordImageMeta.builder()
                .hiveRecordId(hiveRecord.getId())
                .thumbStatus("PENDING")
                .createdAt(now)
                .updatedAt(now)
                .build();
        imageMetaRepository.save(meta);

        Thread.ofVirtual().start(() -> doGenerateAndUpload(hiveRecord, fileKey, stagingFile, meta));
    }

    private void doGenerateAndUpload(HiveRecord hiveRecord, String fileKey, File stagingFile, HiveRecordImageMeta meta) {
        try {
            var thumbOpt = thumbnailGeneratorService.generateFromFile(stagingFile);
            if (thumbOpt.isEmpty()) {
                markThumbFailed(meta, fileKey);
                return;
            }
            var thumb = thumbOpt.get();
            String thumbKey = ThumbnailKeyHelper.thumbKey(fileKey);
            String thumbFileName = ThumbnailKeyHelper.thumbFileNameForEncryption(fileKey);
            HiveOssTask thumbTask = HiveOssTask.createTask()
                    .withBucket(hiveRecord.getBucketName())
                    .withKey(thumbKey)
                    .withInputStream(new ByteArrayInputStream(thumb.thumbJpeg()))
                    .withStorageClass(CategoryStorageClass.STANDARD.name())
                    .withEncryption(thumbFileName);
            hiveOssService.using(hiveRecord.getProvider()).uploadSync(thumbTask);
            meta.setThumbKey(thumbKey);
            meta.setThumbStatus("READY");
            meta.setImageWidth(thumb.imageWidth());
            meta.setImageHeight(thumb.imageHeight());
            meta.setUpdatedAt(LocalDateTime.now());
            imageMetaRepository.save(meta);
            log.info("thumbnail ready fileKey={}", fileKey);
            try {
                FileUtils.writeByteArrayToFile(thumbnailCacheFileFor(fileKey), thumb.thumbJpeg());
            } catch (IOException e) {
                log.warn("failed to write thumbnail cache fileKey={}", fileKey, e);
            }
        } catch (Exception e) {
            log.warn("thumbnail generation failed for fileKey={}", fileKey, e);
            markThumbFailed(meta, fileKey);
        }
    }

    private void markThumbFailed(HiveRecordImageMeta meta, String fileKey) {
        try {
            meta.setThumbStatus("FAILED");
            meta.setUpdatedAt(LocalDateTime.now());
            imageMetaRepository.save(meta);
        } catch (Exception e) {
            log.error("failed to mark thumbnail FAILED for fileKey={}", fileKey, e);
        }
    }

    private void recoverPendingOnStartup() {
        List<HiveRecordImageMeta> pendingList = imageMetaRepository.findByThumbStatus("PENDING");
        if (pendingList.isEmpty()) {
            return;
        }
        log.info("recovering {} pending thumbnail(s) on startup", pendingList.size());
        for (HiveRecordImageMeta meta : pendingList) {
            Optional<HiveRecord> recordOpt = hiveRecordRepository.findById(meta.getHiveRecordId());
            if (recordOpt.isEmpty()) {
                log.warn("thumbnail meta {} has no matching HiveRecord, marking FAILED", meta.getId());
                markThumbFailed(meta, "unknown");
                continue;
            }
            HiveRecord record = recordOpt.get();
            String fileKey = record.getFileKey();
            File stagingFile = cacheFileFor(fileKey);
            if (!stagingFile.isFile()) {
                log.warn("thumbnail staging file missing on startup, marking FAILED fileKey={}", fileKey);
                markThumbFailed(meta, fileKey);
                continue;
            }
            log.info("resubmitting pending thumbnail on startup fileKey={}", fileKey);
            Thread.ofVirtual().start(() -> doGenerateAndUpload(record, fileKey, stagingFile, meta));
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void evictExpiredCacheFiles() {
        long cutoffMs = System.currentTimeMillis() - (long) imageCacheTtlDays * 86_400_000L;

        List<HiveRecordImageMeta> pending = imageMetaRepository.findByThumbStatus("PENDING");
        List<Integer> pendingIds = pending.stream().map(HiveRecordImageMeta::getHiveRecordId).toList();
        Set<String> pendingFileKeys = hiveRecordRepository.findAllById(pendingIds)
                .stream().map(HiveRecord::getFileKey).filter(Objects::nonNull).collect(Collectors.toSet());

        File cacheDir = new File(imageCacheDir);
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith("_src") || name.endsWith("_thumb"));
        if (files == null) return;

        for (File file : files) {
            String fileKey = file.getName().replaceAll("_(src|thumb)$", "");
            if (pendingFileKeys.contains(fileKey)) continue;
            if (file.lastModified() < cutoffMs) {
                FileUtils.deleteQuietly(file);
                log.debug("evicted image cache file fileKey={}", fileKey);
            }
        }
    }

    public void deleteCacheFiles(String fileKey) {
        FileUtils.deleteQuietly(cacheFileFor(fileKey));
        FileUtils.deleteQuietly(thumbnailCacheFileFor(fileKey));
    }

    private File cacheFileFor(String fileKey) {
        return new File(imageCacheDir, fileKey + "_src");
    }

    private File thumbnailCacheFileFor(String fileKey) {
        return new File(imageCacheDir, fileKey + "_thumb");
    }
}
