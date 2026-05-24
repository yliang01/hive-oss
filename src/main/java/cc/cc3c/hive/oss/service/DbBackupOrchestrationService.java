package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.oss.controller.vo.DbBackupListVO;
import cc.cc3c.hive.oss.tools.H2RestoreTool;
import cc.cc3c.hive.oss.vendor.HiveOss;
import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.db2oss.manifest.BackupManifest;
import cc.cc3c.hive.db2oss.model.BackupArtifacts;
import cc.cc3c.hive.db2oss.model.RestoreResult;
import cc.cc3c.hive.db2oss.service.BackupService;
import cc.cc3c.hive.db2oss.service.RestoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DbBackupOrchestrationService {
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "db-backup-async");
        t.setDaemon(true);
        return t;
    });

    private final BackupService backupService;
    private final RestoreService restoreService;
    private final HiveOssService hiveOssService;
    private final DbBackupManifestService manifestService;
    private final DbBackupChecksumVerifier checksumVerifier;
    private final DbRestoreStatusTracker restoreStatusTracker;
    private final DbBackupQueryService dbBackupQueryService;
    private final AlibabaOssConfig alibabaOssConfig;

    public String startBackup() {
        return backupService.backup(this::uploadBackupArtifacts);
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
        CompletableFuture.runAsync(() -> runRestoreAsync(batchId), ASYNC_EXECUTOR);
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
            BackupManifest manifest = BackupManifest.read(manifestOut.toByteArray());
            String archiveKey = toBackupObjectKey(manifest.archiveFile());
            String checksumKey = toBackupObjectKey(manifest.checksumFile());
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
            Path backupDir = Path.of(backupService.backupDir());
            manifestPath = backupDir.resolve(manifestService.manifestFileName(batchId));
            try (FileOutputStream out = new FileOutputStream(manifestPath.toFile())) {
                HiveOssTask task = HiveOssTask.createTask()
                        .withBucket(alibabaOssConfig.getBackupBucket())
                        .withKey(manifestService.manifestKey(batchId))
                        .withOutputStream(out)
                        .withEncryption(manifestService.manifestFileName(batchId));
                hiveOssService.using(HiveStorageProvider.ALIBABA).download(task);
            }

            BackupManifest manifest = BackupManifest.read(manifestPath);
            if (!isRestoreCompatible(manifest.databaseType())) {
                throw new IllegalStateException("backup database type " + manifest.databaseType()
                        + " cannot be restored into current database type " + backupService.databaseType());
            }
            String archiveFileName = manifest.archiveFile();
            String checksumFileName = manifest.checksumFile();
            String archiveKey = toBackupObjectKey(archiveFileName);
            String checksumKey = toBackupObjectKey(checksumFileName);
            log.info("Restore keys resolved from manifest file names, batchId={}", batchId);

            archivePath = backupDir.resolve(archiveFileName);
            checksumPath = backupDir.resolve(checksumFileName);

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

            if ("h2".equalsIgnoreCase(backupService.databaseType())) {
                writePendingH2RestoreRequest(batchId, archivePath, manifest.database());
                archivePath = null;
                log.info("H2 restore request created for batchId={}; restart app to apply", batchId);
                restoreStatusTracker.markRestartNeeded(batchId, "restore downloaded; restart application to apply H2 restore");
                return;
            }

            RestoreResult result = restoreService.restore(archivePath);
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


    private boolean isRestoreCompatible(String backupDatabaseType) {
        return backupDatabaseType == null || backupDatabaseType.isBlank()
                || backupDatabaseType.equalsIgnoreCase(backupService.databaseType());
    }

    private void writePendingH2RestoreRequest(String batchId, Path archivePath, String backupDatabaseName) throws Exception {
        H2RestoreTool.writePendingRequest(batchId, archivePath, backupDatabaseName);
    }

}
