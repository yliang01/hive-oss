package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveSyncVO;
import cc.cc3c.hive.oss.db.service.DbBackupManifestService;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HiveSyncService {

    private final HiveOssService hiveOssService;

    private final HiveRecordRepository hiveRecordRepository;
    private final FileCategoryRepository fileCategoryRepository;
    private final HiveSyncSummaryService hiveSyncSummaryService;
    private final HiveRecordStateService stateService;
    private final ConcurrentHashMap<Long, ReentrantLock> syncLocks = new ConcurrentHashMap<>();

    public HiveSyncService(HiveOssService hiveOssService,
                            HiveRecordRepository hiveRecordRepository,
                            FileCategoryRepository fileCategoryRepository,
                            HiveSyncSummaryService hiveSyncSummaryService,
                            HiveRecordStateService stateService) {
        this.hiveOssService = hiveOssService;
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileCategoryRepository = fileCategoryRepository;
        this.hiveSyncSummaryService = hiveSyncSummaryService;
        this.stateService = stateService;
    }

    public HiveSyncVO syncRemote(Long categoryId, String bucketName, CategoryStorageClass storageClass) throws Exception {
        ReentrantLock lock = syncLocks.computeIfAbsent(categoryId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            FileCategoryEntity category = fileCategoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryId));
            throw new SyncAlreadyRunningException(category.getCode());
        }
        try {
            FileCategoryEntity category = fileCategoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryId));
            HiveOssTask task = HiveOssTask.createTask().withBucket(bucketName);
            Map<String, HiveOssObject> objectMap = hiveOssService.using(HiveStorageProvider.ALIBABA)
                    .listObjects(task)
                    .stream()
                    .filter(object -> !isBackupManagedObject(object.getFileKey()) && !isThumbnailObject(object.getFileKey()))
                    .collect(Collectors.toMap(HiveOssObject::getFileKey, v -> v));

            List<HiveRecord> recordList = hiveRecordRepository.findByCategory_IdAndDeletedIsFalse(categoryId);
            Map<String, HiveRecord> recordMap = recordList.stream().collect(Collectors.toMap(HiveRecord::getFileKey, v -> v));

            SyncCounters counters = new SyncCounters();
            for (Map.Entry<String, HiveOssObject> entry : objectMap.entrySet()) {
                String fileKey = entry.getKey();
                HiveOssObject hiveOssObject = entry.getValue();
                HiveRecord hiveRecord = recordMap.get(fileKey);
                if (hiveRecord != null) {
                    counters.add(stateService.applyRemoteFound(hiveRecord, category, hiveOssObject, storageClass));
                } else {
                    hiveRecord = stateService.discoverRemoteObject(category, hiveOssObject);
                    recordList.add(hiveRecord);
                    counters.add(HiveRecordStateService.SyncOutcome.OSS_ONLY);
                }
            }

            for (Map.Entry<String, HiveRecord> entry : recordMap.entrySet()) {
                String fileKey = entry.getKey();
                HiveOssObject hiveOssObject = objectMap.get(fileKey);
                if (hiveOssObject == null) {
                    HiveRecord hiveRecord = entry.getValue();
                    counters.add(stateService.applyRemoteMissing(hiveRecord));
                }
            }
            hiveRecordRepository.saveAll(recordList);
            HiveSyncVO result = HiveSyncVO.builder()
                    .status("SUCCESS")
                    .syncedAt(LocalDateTime.now())
                    .ossTotal(objectMap.size())
                    .ossOnly(counters.ossOnly)
                    .ossToDbMatched(counters.ossToDbMatched)
                    .ossToDbMismatched(counters.ossToDbMismatched)
                    .dbToOssMismatched(counters.dbToOssMismatched)
                    .skipped(counters.skipped)
                    .build();
            hiveSyncSummaryService.markSuccess(categoryId, result);
            return result;
        } catch (Exception e) {
            hiveSyncSummaryService.markFailed(categoryId, e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private static final String THUMB_PREFIX = "thumb/";
    private static final String THUMB_SUFFIX = "_w320.jpg";

    private boolean isBackupManagedObject(String fileKey) {
        return fileKey != null && fileKey.startsWith(DbBackupManifestService.DB_BACKUP_KEY_PREFIX);
    }

    /** Exclude thumbnail objects (e.g. thumb/{fileKey}_w320.jpg) from syncing into hive_record. */
    private boolean isThumbnailObject(String fileKey) {
        return fileKey != null && fileKey.startsWith(THUMB_PREFIX) && fileKey.endsWith(THUMB_SUFFIX);
    }

    private static class SyncCounters {
        private int ossOnly;
        private int ossToDbMatched;
        private int ossToDbMismatched;
        private int dbToOssMismatched;
        private int skipped;

        private void add(HiveRecordStateService.SyncOutcome outcome) {
            switch (outcome) {
                case MATCHED -> ossToDbMatched++;
                case OSS_ONLY -> ossOnly++;
                case OSS_TO_DB_MISMATCHED -> ossToDbMismatched++;
                case DB_TO_OSS_MISSING -> dbToOssMismatched++;
                case SKIPPED -> skipped++;
            }
        }
    }

}
