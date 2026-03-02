package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.oss.controller.vo.DbBackupListVO;
import cc.cc3c.hive.oss.vendor.HiveOss;
import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.backup.config.BackupProperties;
import com.example.backup.model.BackupArtifacts;
import com.example.backup.model.RestoreResult;
import com.example.backup.service.BackupService;
import com.example.backup.service.RestoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DbBackupOrchestrationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BackupService backupService;
    private final RestoreService restoreService;
    private final BackupProperties backupProperties;
    private final HiveOssService hiveOssService;
    private final DbBackupManifestService manifestService;
    private final DbBackupChecksumVerifier checksumVerifier;
    private final DbRestoreStatusTracker restoreStatusTracker;
    private final DbBackupQueryService dbBackupQueryService;
    private final AlibabaOssConfig alibabaOssConfig;

    public String startBackup() {
        String database = backupProperties.getMysql().getDatabase();
        String batchId = database + "-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        CompletableFuture.runAsync(() -> runBackupAsync(batchId), Executors.newCachedThreadPool());
        return batchId;
    }

    private void runBackupAsync(String batchId) {
        try {
            backupService.backup(backupProperties, batchId, artifacts -> uploadBackupArtifacts(artifacts));
            log.info("Backup uploaded to OSS, batchId={}", batchId);
        } catch (Exception e) {
            log.error("Backup failed for batchId={}", batchId, e);
        }
    }

    private void uploadBackupArtifacts(BackupArtifacts artifacts) throws Exception {
        Path gzipFile = artifacts.getGzipFile();
        Path checksumFile = artifacts.getChecksumFile();
        Path manifestFile = artifacts.getManifestFile();

        String batchId = artifacts.getBatchId();
        String gzipFileName = gzipFile.getFileName().toString();
        String checksumFileName = checksumFile.getFileName().toString();
        String manifestFileName = manifestFile.getFileName().toString();

        String archiveKey = toBackupObjectKey(gzipFileName);
        String checksumKey = toBackupObjectKey(checksumFileName);
        String manifestKey = toBackupObjectKey(manifestFileName);

        log.info("Upload keys resolved from manifest file names, batchId={}", batchId);

        HiveOss oss = hiveOssService.using(HiveStorageProvider.ALIBABA);
        String backupBucket = alibabaOssConfig.getBackupBucket();
        try (FileInputStream gzipIn = new FileInputStream(gzipFile.toFile())) {
            HiveOssTask task = HiveOssTask.createTask()
                    .withBucket(backupBucket)
                    .withKey(archiveKey)
                    .withInputStream(gzipIn)
                    .withEncryption(gzipFileName);
            oss.uploadSync(task);
        }
        try (FileInputStream checksumIn = new FileInputStream(checksumFile.toFile())) {
            HiveOssTask task = HiveOssTask.createTask()
                    .withBucket(backupBucket)
                    .withKey(checksumKey)
                    .withInputStream(checksumIn)
                    .withEncryption(checksumFileName);
            oss.uploadSync(task);
        }
        try (FileInputStream manifestIn = new FileInputStream(manifestFile.toFile())) {
            HiveOssTask task = HiveOssTask.createTask()
                    .withBucket(backupBucket)
                    .withKey(manifestKey)
                    .withInputStream(manifestIn)
                    .withEncryption(manifestFileName);
            oss.uploadSync(task);
        }
    }

    public void startRestore(String batchId) {
        HiveOss oss = hiveOssService.using(HiveStorageProvider.ALIBABA);
        String manifestKey = manifestService.manifestKey(batchId);
        HiveOssTask task = HiveOssTask.createTask().withBucket(alibabaOssConfig.getBackupBucket()).withKey(manifestKey);
        if (!oss.doesObjectExist(task)) {
            throw new IllegalArgumentException("manifest not found for batch: " + batchId);
        }
        restoreStatusTracker.markPending(batchId, "restore task accepted");
        CompletableFuture.runAsync(() -> runRestoreAsync(batchId), Executors.newCachedThreadPool());
    }

    public void deleteBackup(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId is required");
        }
        if (isLatestBackup(batchId)) {
            throw new IllegalStateException("latest backup cannot be deleted");
        }
        HiveOss oss = hiveOssService.using(HiveStorageProvider.ALIBABA);
        String manifestKey = manifestService.manifestKey(batchId);
        HiveOssTask manifestTask = HiveOssTask.createTask()
                .withBucket(alibabaOssConfig.getBackupBucket())
                .withKey(manifestKey);
        if (!oss.doesObjectExist(manifestTask)) {
            throw new IllegalArgumentException("manifest not found for batch: " + batchId);
        }
        try (ByteArrayOutputStream manifestOut = new ByteArrayOutputStream()) {
            HiveOssTask downloadTask = HiveOssTask.createTask()
                    .withBucket(alibabaOssConfig.getBackupBucket())
                    .withKey(manifestKey)
                    .withOutputStream(manifestOut)
                    .withEncryption(manifestService.manifestFileName(batchId));
            oss.download(downloadTask);
            ManifestNames manifestNames = parseManifestNames(manifestOut.toByteArray());
            String archiveKey = toBackupObjectKey(manifestNames.archiveFile());
            String checksumKey = toBackupObjectKey(manifestNames.checksumFile());
            deleteBackupObject(oss, archiveKey);
            deleteBackupObject(oss, checksumKey);
            deleteBackupObject(oss, manifestKey);
            log.info("Backup deleted from OSS, batchId={}", batchId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("failed to delete backup: " + batchId, e);
        }
    }

    private void runRestoreAsync(String batchId) {
        Path archivePath = null;
        Path checksumPath = null;
        Path manifestPath = null;
        try {
            Path tempDir = Path.of(backupProperties.getRestore().getTempDir());
            Files.createDirectories(tempDir);
            manifestPath = tempDir.resolve(manifestService.manifestFileName(batchId));
            try (FileOutputStream out = new FileOutputStream(manifestPath.toFile())) {
                HiveOssTask task = HiveOssTask.createTask()
                        .withBucket(alibabaOssConfig.getBackupBucket())
                        .withKey(manifestService.manifestKey(batchId))
                        .withOutputStream(out)
                        .withEncryption(manifestService.manifestFileName(batchId));
                hiveOssService.using(HiveStorageProvider.ALIBABA).download(task);
            }

            ManifestNames manifestNames = parseManifestNames(manifestPath);
            String archiveFileName = manifestNames.archiveFile();
            String checksumFileName = manifestNames.checksumFile();
            String archiveKey = toBackupObjectKey(archiveFileName);
            String checksumKey = toBackupObjectKey(checksumFileName);
            log.info("Restore keys resolved from manifest file names, batchId={}", batchId);

            archivePath = tempDir.resolve(archiveFileName);
            checksumPath = tempDir.resolve(checksumFileName);

            try (FileOutputStream out = new FileOutputStream(archivePath.toFile())) {
                HiveOssTask task = HiveOssTask.createTask()
                        .withBucket(alibabaOssConfig.getBackupBucket())
                        .withKey(archiveKey)
                        .withOutputStream(out)
                        .withEncryption(archiveFileName);
                hiveOssService.using(HiveStorageProvider.ALIBABA).download(task);
            }

            try (FileOutputStream out = new FileOutputStream(checksumPath.toFile())) {
                HiveOssTask task = HiveOssTask.createTask()
                        .withBucket(alibabaOssConfig.getBackupBucket())
                        .withKey(checksumKey)
                        .withOutputStream(out)
                        .withEncryption(checksumFileName);
                hiveOssService.using(HiveStorageProvider.ALIBABA).download(task);
            }

            checksumVerifier.verifySha256(archivePath, checksumPath);

            RestoreResult result = restoreService.restore(backupProperties, archivePath);
            if (result.mysqlExitCode() != 0) {
                throw new RuntimeException("mysql import exit code: " + result.mysqlExitCode());
            }
            log.info("Restore completed for batchId={}", batchId);
            restoreStatusTracker.markRestored(batchId, "restore completed");
        } catch (Exception e) {
            log.error("Restore failed for batchId={}", batchId, e);
            restoreStatusTracker.markFailed(batchId, e.getMessage() == null ? "restore failed" : e.getMessage());
        } finally {
            if (archivePath != null && Files.exists(archivePath)) {
                try {
                    Files.delete(archivePath);
                } catch (Exception ignored) {}
            }
            if (checksumPath != null && Files.exists(checksumPath)) {
                try {
                    Files.delete(checksumPath);
                } catch (Exception ignored) {}
            }
            if (manifestPath != null && Files.exists(manifestPath)) {
                try {
                    Files.delete(manifestPath);
                } catch (Exception ignored) {}
            }
        }
    }

    private String toBackupObjectKey(String fileName) {
        return DbBackupManifestService.DB_BACKUP_KEY_PREFIX + fileName;
    }

    private ManifestNames parseManifestNames(Path manifestPath) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(manifestPath.toFile());
        return parseManifestNames(root);
    }

    private ManifestNames parseManifestNames(byte[] manifestContent) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(manifestContent);
        return parseManifestNames(root);
    }

    private ManifestNames parseManifestNames(JsonNode root) {
        String archiveFile = textValue(root, "archiveFile");
        String checksumFile = textValue(root, "checksumFile");
        return new ManifestNames(archiveFile, checksumFile);
    }

    private boolean isLatestBackup(String batchId) {
        List<DbBackupListVO.DbBackupItemVO> items = dbBackupQueryService.listBackups().getItems();
        if (items == null || items.isEmpty()) {
            return false;
        }
        String latestBatchId = items.get(0).getBatchId();
        return Objects.equals(latestBatchId, batchId);
    }

    private void deleteBackupObject(HiveOss oss, String key) throws Exception {
        HiveOssTask deleteTask = HiveOssTask.createTask()
                .withBucket(alibabaOssConfig.getBackupBucket())
                .withKey(key);
        oss.delete(deleteTask);
    }

    private String textValue(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        String value = node == null ? null : node.asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("manifest missing required field: " + fieldName);
        }
        return value;
    }

    private record ManifestNames(String archiveFile, String checksumFile) {
    }
}
