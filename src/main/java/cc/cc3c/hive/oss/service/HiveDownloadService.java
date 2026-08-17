package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.thumbnail.ThumbnailKeyHelper;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.web.CategoryFileContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
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
            if (Files.exists(configuredRoot, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(configuredRoot)
                    || !Files.isDirectory(configuredRoot, LinkOption.NOFOLLOW_LINKS))) {
                throw new IllegalArgumentException("下载根目录不是安全目录: " + configuredRoot);
            }
            Files.createDirectories(configuredRoot);
            if (Files.isSymbolicLink(configuredRoot)) {
                throw new IllegalArgumentException("下载根目录不能是符号链接: " + configuredRoot);
            }
            downloadRoot = configuredRoot.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("无法创建下载根目录: " + downloadDir, e);
        }
    }

    private final ConcurrentHashMap<String, HiveOssTask> downloadTasks = new ConcurrentHashMap<>();

    public HiveOssTask getDownLoadTask(HiveRecord hiveRecord) {
        return downloadTasks.get(taskMapKey(hiveRecord));
    }

    public HiveOssTask getDownLoadTask(CategoryFileContext file) {
        return downloadTasks.get(taskMapKey(file));
    }

    public boolean isDownloadFileAvailable(CategoryFileContext context) {
        try {
            return Files.isRegularFile(resolveDownloadFile(context, false), LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | IllegalArgumentException e) {
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

    public boolean hasLocalDownloadOrIsDownloading(String categoryCode, HiveRecord record) {
        if (HiveDownloadStatus.downloading == record.getDownloadStatus()) {
            return true;
        }
        String groupCode = record.getGroup() == null ? null : record.getGroup().getGroupCode();
        try {
            return Files.isRegularFile(resolveDownloadFile(categoryCode, groupCode, record.getZipped(), record.getFileName(), false),
                    LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
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

    public void download(CategoryFileContext context) throws Exception {
        if (downloadTasks.containsKey(taskMapKey(context))) {
            return;
        }
        Path targetFile = resolveDownloadFile(context, true);
        Path partFile = getDownloadPartFile(context);
        Files.deleteIfExists(partFile);
        HiveOssTask task = createOutputTask(context, new FileOutputStream(partFile.toFile()));
        log.info("download start {}", task);
        String taskMapKey = taskMapKey(context);
        Long categoryId = context.category().categoryId();
        hiveRecordRepository.updateDownloadStatus(categoryId, task.getKey(), HiveDownloadStatus.downloading);
        downloadTasks.put(taskMapKey, task);
        HiveOssTask finalTask = task;
        Thread.ofVirtual().start(() -> {
            try {
                hiveOssService.using(context.provider()).download(finalTask);
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
        Path downloadFile = resolveDownloadFile(context, false);
        if (Files.exists(downloadFile, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(downloadFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("local download target is not a regular file: " + downloadFile);
        }
        Files.deleteIfExists(downloadFile);
        hiveRecordRepository.updateDownloadStatus(context.category().categoryId(), context.key(), null);
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
        if (!Files.isRegularFile(downloadFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileNotFoundException("本地下载副本不存在: " + context.key());
        }
        return downloadFile;
    }

    private Path getDownloadPartFile(CategoryFileContext context) throws IOException {
        Path downloadFile = resolveDownloadFile(context, false);
        return downloadFile.resolveSibling(downloadFile.getFileName() + ".part");
    }

    private Path resolveDownloadFile(CategoryFileContext context, boolean createDirectories) throws IOException {
        return resolveDownloadFile(context.category().code(), context.groupCode(), context.zipped(), context.fileName(), createDirectories);
    }

    private Path resolveDownloadFile(String categoryCode, String groupCode, Boolean zipped, String fileName,
                                     boolean createDirectories) throws IOException {
        requireSafePathSegment(categoryCode, "分类编码");
        String resolvedGroupCode = StringUtils.defaultIfBlank(groupCode, UNGROUPED_DIR);
        requireSafePathSegment(resolvedGroupCode, "分组编码");
        requireSafePathSegment(fileName, "文件名");
        String resolvedFileName = Boolean.TRUE.equals(zipped) ? fileName + ".zip" : fileName;
        requireSafePathSegment(resolvedFileName, "下载文件名");

        Path categoryDirectory = managedDirectory(downloadRoot.resolve(categoryCode), createDirectories);
        Path groupDirectory = managedDirectory(categoryDirectory.resolve(resolvedGroupCode), createDirectories);
        Path downloadFile = groupDirectory.resolve(resolvedFileName).normalize();
        if (!downloadFile.startsWith(groupDirectory)) {
            throw new IllegalArgumentException("下载文件路径超出允许范围: " + fileName);
        }
        if (Files.exists(downloadFile, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(downloadFile)) {
            throw new IllegalArgumentException("下载文件不能是符号链接: " + downloadFile);
        }
        return downloadFile;
    }

    private Path managedDirectory(Path directory, boolean createDirectories) throws IOException {
        Path normalized = directory.normalize();
        if (!normalized.startsWith(downloadRoot)) {
            throw new IllegalArgumentException("下载目录超出允许范围: " + directory);
        }
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && createDirectories) {
            Files.createDirectory(normalized);
        }
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("下载目录不是安全目录: " + normalized);
            }
            Path realPath = normalized.toRealPath();
            if (!realPath.startsWith(downloadRoot)) {
                throw new IllegalArgumentException("下载目录超出允许范围: " + normalized);
            }
        }
        return normalized;
    }

    private void requireSafePathSegment(String value, String label) {
        if (value == null || value.isBlank() || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException(label + "不能用于下载路径: " + value);
        }
        try {
            Path path = Paths.get(value);
            if (path.isAbsolute() || path.getNameCount() != 1 || !value.equals(path.getFileName().toString())) {
                throw new IllegalArgumentException(label + "不能用于下载路径: " + value);
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(label + "不能用于下载路径: " + value, e);
        }
    }
}
