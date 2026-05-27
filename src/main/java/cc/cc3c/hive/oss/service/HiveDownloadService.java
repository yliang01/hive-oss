package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.thumbnail.ThumbnailGeneratorService;
import cc.cc3c.hive.oss.thumbnail.ThumbnailKeyHelper;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HiveDownloadService {

    private final HiveOssService hiveOssService;
    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final ThumbnailGeneratorService thumbnailGeneratorService;

    @Value("${hive.imageCacheDir}")
    private String imageCacheDir;

    public HiveDownloadService(HiveRecordRepository hiveRecordRepository,
                              HiveRecordImageMetaRepository imageMetaRepository,
                              HiveOssService hiveOssService,
                              ThumbnailGeneratorService thumbnailGeneratorService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.hiveOssService = hiveOssService;
        this.thumbnailGeneratorService = thumbnailGeneratorService;
    }

    private String downloadDir;

    private File downloadFolder;

    @Value("${hive.downloadDir}")
    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }

    @PostConstruct
    public void init() {
        downloadFolder = new File(downloadDir);
        try {
            FileUtils.forceMkdir(downloadFolder);
        } catch (IOException e) {
            log.error("fail to create download folder", e);
            System.exit(1);
        }
    }

    private final ConcurrentHashMap<String, HiveOssTask> downloadTasks = new ConcurrentHashMap<>();

    public HiveOssTask getDownLoadTask(HiveRecord hiveRecord) {
        return downloadTasks.get(taskMapKey(hiveRecord));
    }

    public void reconcileStaleDownloading(HiveRecord hiveRecord) {
        if (HiveDownloadStatus.downloading == hiveRecord.getDownloadStatus()
                && !downloadTasks.containsKey(taskMapKey(hiveRecord))) {
            FileUtils.deleteQuietly(getDownloadPartFile(hiveRecord));
            hiveRecordRepository.updateDownloadStatus(hiveRecord.getCategory().getId(), hiveRecord.getFileKey(), HiveDownloadStatus.failed);
            hiveRecord.setDownloadStatus(HiveDownloadStatus.failed);
        }
    }

    public void download(HiveRecord hiveRecord) throws Exception {
        reconcileStaleDownloading(hiveRecord);
        if (HiveDownloadStatus.downloading == hiveRecord.getDownloadStatus()) {
            return;
        }
        File targetFile = getDownloadFile(hiveRecord);
        File partFile = getDownloadPartFile(hiveRecord);
        FileUtils.deleteQuietly(partFile);
        HiveOssTask task = createOutputTask(hiveRecord, new FileOutputStream(partFile));
        log.info("download start {}", task);
        String taskMapKey = taskMapKey(hiveRecord);
        Long categoryId = hiveRecord.getCategory().getId();
        hiveRecordRepository.updateDownloadStatus(categoryId, task.getKey(), HiveDownloadStatus.downloading);
        downloadTasks.put(taskMapKey, task);
        HiveOssTask finalTask = task;
        Thread.ofVirtual().start(() -> {
            try {
                hiveOssService.using(hiveRecord.getProvider()).download(finalTask);
                Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                hiveRecordRepository.updateDownloadStatus(categoryId, finalTask.getKey(), HiveDownloadStatus.success);
                log.info("download finished {}", finalTask);
            } catch (Exception e) {
                log.error("download failed {}", finalTask, e);
                FileUtils.deleteQuietly(partFile);
                hiveRecordRepository.updateDownloadStatus(categoryId, finalTask.getKey(), HiveDownloadStatus.failed);
            } finally {
                downloadTasks.remove(taskMapKey);
            }
        });
    }

    public void streamPreview(HiveRecord hiveRecord, OutputStream outputStream, String uiVariant) throws Exception {
        boolean useCache = "image".equalsIgnoreCase(uiVariant)
                && thumbnailGeneratorService.isSupportedImage(hiveRecord.getFileName());
        if (useCache) {
            String fileKey = hiveRecord.getFileKey();
            File cacheFile = new File(imageCacheDir, fileKey + "_src");
            if (!cacheFile.isFile()) {
                File tmpFile = new File(imageCacheDir, fileKey + "_src.tmp");
                try {
                    try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                        HiveOssTask task = createOutputTask(hiveRecord, fos);
                        hiveOssService.using(hiveRecord.getProvider()).download(task);
                    }
                    tmpFile.renameTo(cacheFile);
                } catch (Exception e) {
                    FileUtils.deleteQuietly(tmpFile);
                    throw e;
                }
            }
            try (InputStream in = new FileInputStream(cacheFile)) {
                IOUtils.copy(in, outputStream);
            }
            return;
        }
        HiveOssTask task = createOutputTask(hiveRecord, outputStream);
        hiveOssService.using(hiveRecord.getProvider()).download(task);
    }

    /**
     * Stream thumbnail image (decrypted). Cache-first: serves from local _thumb if present,
     * otherwise downloads from OSS and caches before serving.
     */
    public void streamPreviewThumbnail(HiveRecord hiveRecord, OutputStream outputStream) throws Exception {
        String fileKey = hiveRecord.getFileKey();
        File cacheFile = new File(imageCacheDir, fileKey + "_thumb");
        if (!cacheFile.isFile()) {
            HiveRecordImageMeta meta = imageMetaRepository.findByHiveRecordId(hiveRecord.getId()).orElse(null);
            if (meta == null || !"READY".equals(meta.getThumbStatus()) || StringUtils.isBlank(meta.getThumbKey())) {
                throw new IllegalArgumentException("no thumbnail key for record");
            }
            String thumbFileName = ThumbnailKeyHelper.thumbFileNameForEncryption(fileKey);
            File tmpFile = new File(imageCacheDir, fileKey + "_thumb.tmp");
            try {
                try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                    HiveOssTask task = HiveOssTask.createTask()
                            .withBucket(hiveRecord.getCategory().getBucketName())
                            .withKey(meta.getThumbKey())
                            .withOutputStream(fos)
                            .withEncryption(thumbFileName);
                    hiveOssService.using(hiveRecord.getProvider()).download(task);
                }
                tmpFile.renameTo(cacheFile);
            } catch (Exception e) {
                FileUtils.deleteQuietly(tmpFile);
                throw e;
            }
        }
        try (InputStream in = new FileInputStream(cacheFile)) {
            IOUtils.copy(in, outputStream);
        }
    }

    private HiveOssTask createOutputTask(HiveRecord hiveRecord, OutputStream outputStream) throws Exception {
        HiveOssTask task = HiveOssTask.createTask()
                .withBucket(hiveRecord.getCategory().getBucketName())
                .withKey(hiveRecord.getFileKey())
                .withOutputStream(outputStream);
        if (!HiveRecordStatus.OSS_ONLY.equals(hiveRecord.getStatus())) {
            task = task.withEncryption(hiveRecord.getFileName());
        }
        return task;
    }

    private String taskMapKey(HiveRecord hiveRecord) {
        return hiveRecord.getCategory().getId() + ":" + hiveRecord.getFileKey();
    }

    public File getDownloadFile(HiveRecord hiveRecord) {
        String fileName;
        if (StringUtils.isNotEmpty(hiveRecord.getFileName())) {
            fileName = hiveRecord.getFileName();
        } else {
            fileName = hiveRecord.getFileKey();
        }
        return new File(downloadFolder, hiveRecord.getZipped() ? fileName + ".zip" : fileName);
    }

    private File getDownloadPartFile(HiveRecord hiveRecord) {
        return new File(getDownloadFile(hiveRecord).getAbsolutePath() + ".part");
    }
}
