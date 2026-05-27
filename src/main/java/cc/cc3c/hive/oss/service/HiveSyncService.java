package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveSyncVO;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HiveSyncService {

    private final HiveOssService hiveOssService;

    private final HiveRecordRepository hiveRecordRepository;
    private final FileCategoryRepository fileCategoryRepository;
    private final HiveSyncSummaryService hiveSyncSummaryService;

    public HiveSyncService(HiveOssService hiveOssService,
                            HiveRecordRepository hiveRecordRepository,
                            FileCategoryRepository fileCategoryRepository,
                            HiveSyncSummaryService hiveSyncSummaryService) {
        this.hiveOssService = hiveOssService;
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileCategoryRepository = fileCategoryRepository;
        this.hiveSyncSummaryService = hiveSyncSummaryService;
    }

    public HiveSyncVO syncRemote(Long categoryId, String bucketName, CategoryStorageClass storageClass) throws Exception {
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

            int ossOnly = 0;
            int ossToDbMatched = 0;
            int ossToDbMismatched = 0;
            for (Map.Entry<String, HiveOssObject> entry : objectMap.entrySet()) {
                String fileKey = entry.getKey();
                HiveOssObject hiveOssObject = entry.getValue();
                HiveRecord hiveRecord = recordMap.get(fileKey);
                if (hiveRecord != null) {
                    if (HiveRecordStatus.UPLOADED.equals(hiveRecord.getStatus())) {
                        hiveRecord.setProvider(HiveStorageProvider.ALIBABA);
                        hiveRecord.setCategory(category);
                        hiveRecord.setSize(hiveOssObject.getSize());
                        hiveRecord.setUpdateTime(hiveOssObject.getLastModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                        hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
                        ossToDbMatched++;
                    } else if (HiveRecordStatus.OSS_ONLY.equals(hiveRecord.getStatus())) {
                        ossOnly++;
                    } else {
                        ossToDbMismatched++;
                    }
                } else {
                    hiveRecord = new HiveRecord();
                    hiveRecord.setFileName("");
                    hiveRecord.setFileKey(fileKey);
                    hiveRecord.setZipped(false);
                    hiveRecord.setProvider(HiveStorageProvider.ALIBABA);
                    hiveRecord.setCategory(category);
                    hiveRecord.setSize(hiveOssObject.getSize());
                    hiveRecord.setUpdateTime(hiveOssObject.getLastModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                    hiveRecord.setStatus(HiveRecordStatus.OSS_ONLY);
                    recordList.add(hiveRecord);
                    ossOnly++;
                }
                hiveRecord.setLastSyncTime(LocalDateTime.now());
                if (isArchiveStorageClass(storageClass)) {
                    Duration duration = Duration.between(hiveRecord.getUpdateTime(), LocalDateTime.now());
                    hiveRecord.setDeletable(duration.toDays() > 61);
                } else {
                    hiveRecord.setDeletable(true);
                }
            }

            int dbToOssMismatched = 0;
            for (Map.Entry<String, HiveRecord> entry : recordMap.entrySet()) {
                String fileKey = entry.getKey();
                HiveOssObject hiveOssObject = objectMap.get(fileKey);
                if (hiveOssObject == null) {
                    HiveRecord hiveRecord = entry.getValue();
                    hiveRecord.setStatus(HiveRecordStatus.DB_ONLY);
                    hiveRecord.setDeletable(true);
                    dbToOssMismatched++;
                }
            }
            hiveRecordRepository.saveAll(recordList);
            HiveSyncVO result = HiveSyncVO.builder()
                    .status("SUCCESS")
                    .syncedAt(LocalDateTime.now())
                    .ossTotal(objectMap.size())
                    .ossOnly(ossOnly)
                    .ossToDbMatched(ossToDbMatched)
                    .ossToDbMismatched(ossToDbMismatched)
                    .dbToOssMismatched(dbToOssMismatched)
                    .build();
            hiveSyncSummaryService.markSuccess(categoryId, result);
            return result;
        } catch (Exception e) {
            hiveSyncSummaryService.markFailed(categoryId, e);
            throw e;
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

    private boolean isArchiveStorageClass(CategoryStorageClass storageClass) {
        return storageClass == CategoryStorageClass.ARCHIVE;
    }
}
