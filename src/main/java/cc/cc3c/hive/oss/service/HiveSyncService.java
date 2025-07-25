package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveSyncVO;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cc.cc3c.hive.domain.model.HiveRecordSource.ALIBABA_ACHIEVE;
import static cc.cc3c.hive.domain.model.HiveRecordSource.ALIBABA_STANDARD;

@Slf4j
@Service
public class HiveSyncService {

    private final HiveOssService hiveOssService;

    private final HiveRecordRepository hiveRecordRepository;

    public HiveSyncService(HiveOssService hiveOssService, HiveRecordRepository hiveRecordRepository) {
        this.hiveOssService = hiveOssService;
        this.hiveRecordRepository = hiveRecordRepository;
    }

    public HiveSyncVO syncRemote(HiveRecordSource source) throws Exception {
        HiveOssTask task = HiveOssTask.createTask().withBucket(source);
        Map<String, HiveOssObject> objectMap = hiveOssService.using(source).listObjects(task).stream().collect(Collectors.toMap(HiveOssObject::getFileKey, v -> v));

        List<HiveRecord> recordList = hiveRecordRepository.findBySourceAndDeletedIsFalse(source);
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
                    hiveRecord.setSource(source);
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
                hiveRecord.setSource(source);
                hiveRecord.setSize(hiveOssObject.getSize());
                hiveRecord.setUpdateTime(hiveOssObject.getLastModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                hiveRecord.setStatus(HiveRecordStatus.OSS_ONLY);
                recordList.add(hiveRecord);
                ossOnly++;
            }
            hiveRecord.setLastSyncTime(LocalDateTime.now());
            if (ALIBABA_ACHIEVE.equals(source)) {
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
                hiveRecord.setDeletable(false);
                dbToOssMismatched++;
            }
        }
        hiveRecordRepository.saveAll(recordList);
        return HiveSyncVO.builder()
                .ossTotal(objectMap.size())
                .ossOnly(ossOnly)
                .ossToDbMatched(ossToDbMatched)
                .ossToDbMismatched(ossToDbMismatched)
                .dbToOssMismatched(dbToOssMismatched).build();
    }
}
