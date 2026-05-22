package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.oss.controller.vo.DbBackupAckVO;
import cc.cc3c.hive.oss.controller.vo.DbBackupListVO;
import cc.cc3c.hive.oss.vendor.HiveOss;
import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Provides backup list and ack data from OSS only. Status is UPLOADED when both
 * archive and manifest (.manifest.json) exist under db-backup/.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DbBackupQueryService {

    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_PENDING = "PENDING";

    private final HiveOssService hiveOssService;
    private final DbBackupManifestService manifestService;
    private final DbRestoreStatusTracker restoreStatusTracker;
    private final AlibabaOssConfig alibabaOssConfig;

    /**
     * Returns ack VO for the given batchId. When archive and manifest both exist in OSS, status is UPLOADED;
     * when batchId is valid but not yet uploaded, status is PENDING. Empty only for invalid (null/blank) batchId.
     */
    public Optional<DbBackupAckVO> getByBatchId(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return Optional.empty();
        }
        Optional<DbBackupAckVO> restoreAck = restoreStatusTracker.find(batchId);
        if (restoreAck.isPresent()) {
            return restoreAck;
        }
        HiveOss oss = hiveOssService.using(HiveStorageProvider.ALIBABA);

        String manifestKey = manifestService.manifestKey(batchId);

        HiveOssTask manifestTask = HiveOssTask.createTask()
                .withBucket(alibabaOssConfig.getBackupBucket())
                .withKey(manifestKey);
        if (!oss.doesObjectExist(manifestTask)) {
            return Optional.of(pendingAck(batchId));
        }

        Instant ackTime = null;
        try {
            HiveOssTask listTask = HiveOssTask.createTask()
                    .withBucket(alibabaOssConfig.getBackupBucket())
                    .withKey(manifestKey);
            List<HiveOssObject> list = oss.listObjects(listTask);
            Optional<HiveOssObject> archiveObj = list.stream()
                    .filter(o -> manifestKey.equals(o.getFileKey()))
                    .findFirst();
            if (archiveObj.isPresent() && archiveObj.get().getLastModified() != null) {
                ackTime = archiveObj.get().getLastModified().toInstant();
            }
        } catch (Exception e) {
            log.debug("Could not get lastModified for ackTime, batchId={}", batchId, e);
        }

        return Optional.of(DbBackupAckVO.builder()
                .batchId(batchId)
                .status(STATUS_UPLOADED)
                .ackTime(ackTime)
                .database(DbBackupManifestService.databaseFromBatchId(batchId))
                .message("backup uploaded to oss")
                .build());
    }

    private DbBackupAckVO pendingAck(String batchId) {
        return DbBackupAckVO.builder()
                .batchId(batchId)
                .status(STATUS_PENDING)
                .message("backup task in progress")
                .build();
    }

    /**
     * Lists all backups from OSS (no pagination).
     * Only entries with both archive and manifest present are included; status is always UPLOADED.
     */
    public DbBackupListVO listBackups() {
        HiveOss oss = hiveOssService.using(HiveStorageProvider.ALIBABA);
        List<DbBackupListVO.DbBackupItemVO> items = new ArrayList<>();
        try {
            HiveOssTask listTask = HiveOssTask.createTask()
                    .withBucket(alibabaOssConfig.getBackupBucket())
                    .withKey(DbBackupManifestService.DB_BACKUP_KEY_PREFIX);
            List<HiveOssObject> objects = oss.listObjects(listTask);

            for (HiveOssObject manifestObject : manifestService.listManifestObjects(objects)) {
                String manifestKey = manifestObject.getFileKey();
                String batchId = DbBackupManifestService.deriveBatchIdFromManifestKey(manifestKey);
                if (batchId == null || batchId.isBlank()) {
                    continue;
                }
                Instant createdAt = manifestObject.getLastModified() != null
                        ? manifestObject.getLastModified().toInstant()
                        : null;
                items.add(DbBackupListVO.DbBackupItemVO.builder()
                        .batchId(batchId)
                        .database(DbBackupManifestService.databaseFromBatchId(batchId))
                        .status(STATUS_UPLOADED)
                        .createdAt(createdAt)
                        .build());
            }
            items.sort(Comparator.comparing(DbBackupListVO.DbBackupItemVO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        } catch (Exception e) {
            log.warn("List db-backup objects failed", e);
            return DbBackupListVO.builder().items(Collections.emptyList()).total(0).build();
        }
        return DbBackupListVO.builder()
                .items(items)
                .total(items.size())
                .build();
    }

}
