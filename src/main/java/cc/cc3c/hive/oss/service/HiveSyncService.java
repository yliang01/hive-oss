package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
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

    //    @Scheduled(cron = "0 0 0 1/1 * ?")
    @EventListener(classes = ApplicationReadyEvent.class)
    public void syncOssAndDB() throws Exception {
        sync(ALIBABA_STANDARD, HiveOssTask.createTask().withBucket(ALIBABA_STANDARD));
        log.info("*******************");
        sync(ALIBABA_ACHIEVE, HiveOssTask.createTask().withBucket(ALIBABA_ACHIEVE));
    }


    private void sync(HiveRecordSource source, HiveOssTask task) {
        try {
            Map<String, HiveOssObject> objectMap = hiveOssService.using(source).listObjects(task).stream().collect(Collectors.toMap(HiveOssObject::getFileKey, v -> v));

            List<HiveRecord> recordList = hiveRecordRepository.findBySourceAndDeletedIsFalse(source);
            Map<String, HiveRecord> recordMap = recordList.stream().collect(Collectors.toMap(HiveRecord::getFileKey, v -> v));
            int dbMatchedSize = 0;
            int dbMismatchedSize = 0;
            int ossOnlySize = 0;
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
                        dbMatchedSize++;
                    } else if (HiveRecordStatus.OSS_ONLY.equals(hiveRecord.getStatus())) {
                        ossOnlySize++;
                    } else {
                        dbMismatchedSize++;
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
                    ossOnlySize++;
                }
                hiveRecord.setLastSyncTime(LocalDateTime.now());
                if (ALIBABA_ACHIEVE.equals(source)) {
                    Duration duration = Duration.between(hiveRecord.getUpdateTime(), LocalDateTime.now());
                    hiveRecord.setDeletable(duration.toDays() > 61);
                } else {
                    hiveRecord.setDeletable(true);
                }
            }
            hiveRecordRepository.saveAllAndFlush(recordList);
            log.info("{} OSS actual size: {}", source, objectMap.size());
            log.info("{} OSS->DB matched size: {}", source, dbMatchedSize);
            log.info("{} OSS->DB mismatched only size: {}", source, dbMismatchedSize);
            log.info("{} OSS only size: {}", source, ossOnlySize);
            log.info("####################");
        } catch (Exception e) {
            log.error("sync {} failed", source, e);
        }
    }

    public void syncLocalToRemote(HiveRecordSource source) {
        List<HiveRecord> recordList = hiveRecordRepository.findBySourceAndDeletedIsFalse(source);
        Map<String, HiveRecord> recordMap = recordList.stream().collect(Collectors.toMap(HiveRecord::getFileKey, v -> v));
        for (Map.Entry<String, HiveRecord> entry : recordMap.entrySet()) {
            String fileKey = entry.getKey();
            boolean exist = hiveOssService.using(entry.getValue().getSource()).doesObjectExist(HiveOssTask.createTask().withBucket(source).withKey(fileKey));
            if (!exist) {
                HiveRecord hiveRecord = entry.getValue();
                hiveRecord.setStatus(HiveRecordStatus.DB_ONLY);
                hiveRecord.setDeletable(false);
            }
        }
        hiveRecordRepository.saveAll(recordList);
    }
}
