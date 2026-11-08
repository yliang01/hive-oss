package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.business.thumbnail.ThumbnailKeyHelper;
import cc.cc3c.hive.oss.business.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.business.web.CategoryFileContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HiveDownloadService {
    private static final String UNGROUPED_DIR = "_ungrouped";

    private final HiveOssService hiveOssService;
    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;

    @Value("${hive.imageCacheDir}")
    private String imageCacheDir;

    public HiveDownloadService(HiveRecordRepository hiveRecordRepository,
                               HiveRecordImageMetaRepository imageMetaRepository,
                               HiveOssService hiveOssService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.hiveOssService = hiveOssService;
    }

    private String downloadDir;

    private Path downloadRoot;

    @Value("${hive.downloadDir}")
    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }

    @PostConstruct
    public void init() {
        try {
            Path configuredRoot = Paths.get(downloadDir).toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            downloadRoot = configuredRoot;
        } catch (IOException e) {
            throw new IllegalStateException("无法创建下载根目录: " + downloadDir, e);
        }
    }

    private final ConcurrentHashMap<String, HiveOssTask> downloadTasks = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface LocalFileOperation<T> {
        T run() throws Exception;
    }

    public synchronized <T> T coordinateLocalFileOperation(LocalFileOperation<T> operation) throws Exception {
        return operation.run();
    }

    public HiveOssTask getDownLoadTask(HiveRecord hiveRecord) {
        return downloadTasks.get(taskMapKey(hiveRecord));
    }

    public HiveOssTask getDownLoadTask(CategoryFileContext file) {
        return downloadTasks.get(taskMapKey(file));
    }

    public boolean isDownloadFileAvailable(CategoryFileContext context) {
        try {
            return Files.isRegularFile(resolveDownloadFile(context, false));
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isRestoreRequired(CategoryFileContext context) {
        return CategoryStorageClass.ARCHIVE == context.category().storageClass()
                && (context.restoreTime() == null || context.restoreTime().isBefore(LocalDateTime.now()));
    }

    public boolean isDownloadable(CategoryFileContext context, HiveDownloadStatus downloadStatus) {
        return HiveRecordStatus.UPLOADED == context.status()
                && !isRestoreRequired(context)
                && downloadStatus != HiveDownloadStatus.downloading
                && !isDownloadFileAvailable(context);
    }

    public boolean isDownloading(String categoryCode, HiveRecord record) {
        return HiveDownloadStatus.downloading == record.getDownloadStatus()
                || downloadTasks.containsKey(categoryCode + ":" + record.getFileKey());
    }

    public HiveDownloadStatus reconcileStaleDownloading(CategoryFileContext context) {
        if (HiveDownloadStatus.downloading == context.downloadStatus()
                && !downloadTasks.containsKey(taskMapKey(context))) {
            try {
                Files.deleteIfExists(getDownloadPartFile(context));
            } catch (IOException | IllegalArgumentException e) {
                log.warn("failed to remove stale download part file {}", context.key(), e);
            }
            hiveRecordRepository.updateDownloadStatus(context.category().categoryId(), context.key(), HiveDownloadStatus.failed);
            return HiveDownloadStatus.failed;
        }
        return context.downloadStatus();
    }

    public boolean download(CategoryFileContext context) throws Exception {
        return coordinateLocalFileOperation(() -> startDownload(context));
    }

    private boolean startDownload(CategoryFileContext context) throws Exception {
        if (downloadTasks.containsKey(taskMapKey(context))) {
            return false;
        }
        CategoryFileContext current = currentContext(context);
        HiveDownloadStatus downloadStatus = reconcileStaleDownloading(current);
        if (isRestoreRequired(current) || !isDownloadable(current, downloadStatus)) {
            return false;
        }
        Path targetFile = resolveDownloadFile(current, true);
        Path partFile = getDownloadPartFile(current);
        Files.deleteIfExists(partFile);
        HiveOssTask task = createOutputTask(current, new FileOutputStream(partFile.toFile()));
        log.info("download start {}", task);
        String taskMapKey = taskMapKey(context);
        Long categoryId = current.category().categoryId();
        hiveRecordRepository.updateDownloadStatus(categoryId, task.getKey(), HiveDownloadStatus.downloading);
        downloadTasks.put(taskMapKey, task);
        HiveOssTask finalTask = task;
        Thread.ofVirtual().start(() -> {
            try {
                hiveOssService.using(current.provider()).download(finalTask);
                Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                hiveRecordRepository.updateDownloadStatus(categoryId, finalTask.getKey(), HiveDownloadStatus.success);
                log.info("download finished {}", finalTask);
            } catch (Exception e) {
                log.error("download failed {}", finalTask, e);
                FileUtils.deleteQuietly(partFile.toFile());
                hiveRecordRepository.updateDownloadStatus(categoryId, finalTask.getKey(), HiveDownloadStatus.failed);
            } finally {
                downloadTasks.remove(taskMapKey);
            }
        });
        return true;
    }

    public long getAvailableDownloadFileSize(CategoryFileContext context) throws IOException {
        return Files.size(requireAvailableDownloadFile(context));
    }

    public void streamLocalPreview(CategoryFileContext context, OutputStream outputStream) throws IOException {
        try (InputStream inputStream = Files.newInputStream(requireAvailableDownloadFile(context))) {
            IOUtils.copy(inputStream, outputStream);
        }
    }

    public void streamPreviewThumbnail(CategoryFileContext file, OutputStream outputStream) throws Exception {
        File cacheFile = new File(imageCacheDir, file.key() + "_thumb");
        if (!cacheFile.isFile()) {
            HiveRecordImageMeta meta = imageMetaRepository.findByHiveRecordId(file.recordId()).orElse(null);
            if (meta == null || !"READY".equals(meta.getThumbStatus()) || StringUtils.isBlank(meta.getThumbKey())) {
                throw new IllegalArgumentException("no thumbnail key for record");
            }
            String thumbFileName = ThumbnailKeyHelper.thumbFileNameForEncryption(file.key());
            File tmpFile = new File(imageCacheDir, file.key() + "_thumb.tmp");
            try {
                try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                    HiveOssTask task = HiveOssTask.createTask()
                            .withBucket(file.category().bucketName())
                            .withKey(meta.getThumbKey())
                            .withOutputStream(fos)
                            .withEncryption(thumbFileName);
                    hiveOssService.using(file.provider()).download(task);
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

    public void releaseLocalStatus(CategoryFileContext context) throws IOException {
        try {
            coordinateLocalFileOperation(() -> {
                releaseLocalStatusLocked(context);
                return null;
            });
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("无法释放本地文件", e);
        }
    }

    private void releaseLocalStatusLocked(CategoryFileContext context) throws IOException {
        CategoryFileContext current = currentContext(context);
        if (HiveDownloadStatus.downloading == current.downloadStatus()
                || downloadTasks.containsKey(taskMapKey(current))) {
            throw new FileSystemException(current.key(), null, "本地文件正在下载");
        }
        Path downloadFile = resolveDownloadFile(current, false);
        if (Files.exists(downloadFile) && !Files.isRegularFile(downloadFile)) {
            throw new IOException("local download target is not a regular file: " + downloadFile);
        }
        Files.deleteIfExists(downloadFile);
        hiveRecordRepository.updateDownloadStatus(current.category().categoryId(), current.key(), null);
    }

    public LocalDownloadMove moveLocalDownload(String categoryName, HiveRecord record, String targetGroupName) throws IOException {
        String sourceGroupName = record.getGroup() == null ? null : record.getGroup().getGroupName();
        Path source = resolveDownloadFile(categoryName, sourceGroupName, record.getZipped(), record.getFileName(), false);
        if (!Files.isRegularFile(source)) {
            return null;
        }
        Path target = resolveDownloadFile(categoryName, targetGroupName, record.getZipped(), record.getFileName(), true);
        Files.move(source, target);
        return new LocalDownloadMove(source, target);
    }

    public void restoreLocalDownloadMove(LocalDownloadMove move) throws IOException {
        if (move != null && Files.exists(move.target())) {
            Files.move(move.target(), move.source());
        }
    }

    public record LocalDownloadMove(Path source, Path target) {
    }

    private HiveOssTask createOutputTask(CategoryFileContext file, OutputStream outputStream) throws Exception {
        HiveOssTask task = HiveOssTask.createTask()
                .withBucket(file.category().bucketName())
                .withKey(file.key())
                .withOutputStream(outputStream);
        if (!HiveRecordStatus.OSS_ONLY.equals(file.status())) {
            task = task.withEncryption(file.fileName());
        }
        return task;
    }

    private String taskMapKey(HiveRecord hiveRecord) {
        return hiveRecord.getCategory().getId() + ":" + hiveRecord.getFileKey();
    }

    private String taskMapKey(CategoryFileContext file) {
        return file.category().categoryId() + ":" + file.key();
    }

    public File getDownloadFile(CategoryFileContext context) {
        try {
            return resolveDownloadFile(context, false).toFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("无法解析下载文件路径", e);
        }
    }

    private Path requireAvailableDownloadFile(CategoryFileContext context) throws IOException {
        Path downloadFile = resolveDownloadFile(context, false);
        if (!Files.isRegularFile(downloadFile)) {
            throw new FileNotFoundException("本地下载副本不存在: " + context.key());
        }
        return downloadFile;
    }

    private Path getDownloadPartFile(CategoryFileContext context) throws IOException {
        Path downloadFile = resolveDownloadFile(context, false);
        return downloadFile.resolveSibling(downloadFile.getFileName() + ".part");
    }

    private Path resolveDownloadFile(CategoryFileContext context, boolean createDirectories) throws IOException {
        return resolveDownloadFile(context.category().name(), context.groupName(), context.zipped(), context.fileName(), createDirectories);
    }

    private Path resolveDownloadFile(String categoryName, String groupName, Boolean zipped, String fileName,
                                     boolean createDirectories) throws IOException {
        String resolvedGroupName = StringUtils.defaultIfBlank(groupName, UNGROUPED_DIR);
        String resolvedFileName = Boolean.TRUE.equals(zipped) ? fileName + ".zip" : fileName;
        Path groupDirectory = downloadRoot.resolve(categoryName).resolve(resolvedGroupName);
        if (createDirectories) {
            Files.createDirectories(groupDirectory);
        }
        return groupDirectory.resolve(resolvedFileName);
    }

    private CategoryFileContext currentContext(CategoryFileContext context) {
        List<HiveRecord> records = hiveRecordRepository
                .findWithGroupByCategory_IdAndFileKeyInAndDeletedIsFalse(context.category().categoryId(), List.of(context.key()));
        return records.isEmpty() ? context : CategoryFileContext.from(context.category(), records.getFirst());
    }
}
