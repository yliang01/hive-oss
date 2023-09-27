package cc.cc3c.hive.oss.sync;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.vendor.HiveOssService;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HiveRecordSyncJob {
    @Autowired
    private HiveRecordRepository hiveRecordRepository;
    @Autowired
    private HiveOssService hiveOssService;

    //    @Scheduled(cron = "0 0 0 1/1 * ?")
//    @EventListener(classes = ApplicationReadyEvent.class)
    public void syncRecord() {

        syncFromAlibaba(HiveRecordSource.ALIBABA_ACHIEVE, HiveOssTask.createTask().withAlibabaAchieve());
        syncFromAlibaba(HiveRecordSource.ALIBABA_STANDARD, HiveOssTask.createTask().withAlibabaStandard());
//            for (HiveOssObject ossObjectSummary : objectSummaryList) {
//                if (keyToRecordPojoMap.containsKey(ossObjectSummary.getKey())) {
//                    HiveRecordPojo hiveRecordPojo = keyToRecordPojoMap.get(ossObjectSummary.getKey());
//                    hiveRecordPojo.setSize(ossObjectSummary.getSize());
//                    hiveRecordPojo.setUpdateTime(new Timestamp(ossObjectSummary.getLastModified().getTime()));
//                    hiveRecordDao.update(hiveRecordPojo);
//                } else {
//                    System.out.println(ossObjectSummary);
//                }
//            }


//            log.info("not exist in oss");
//            for (Map.Entry<String, HiveRecordPojo> entry : keyToRecordMap.entrySet()) {
//                if (!keyToObjectMap.containsKey(entry.getKey())) {
//                    log.info("{}", entry.getValue());
//                }
//            }
    }

    private void syncFromAlibaba(HiveRecordSource source, HiveOssTask task) {
        try {
            Map<String, HiveOssObject> keyToObjectMap = hiveOssService.alibabaOss().listObjects(task).stream().collect(Collectors.toMap(HiveOssObject::getFileKey, v -> v));
            int uploadedSize = 0;
            int ossOnlySize = 0;
            for (Map.Entry<String, HiveOssObject> entry : keyToObjectMap.entrySet()) {
                String fileKey = entry.getKey();
                HiveOssObject hiveOssObject = entry.getValue();
                Optional<HiveRecord> hiveRecordOptional = hiveRecordRepository.findByFileKey(fileKey);
                HiveRecord hiveRecord;
                if (hiveRecordOptional.isPresent()) {
                    hiveRecord = hiveRecordOptional.get();
                    if (!HiveRecordStatus.OSS_ONLY.equals(hiveRecord.getStatus())) {
                        hiveRecord.setSource(source);
                        hiveRecord.setSize(hiveOssObject.getSize());
                        hiveRecord.setUpdateTime(hiveOssObject.getLastModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                        hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
                        uploadedSize++;
                    } else {
                        ossOnlySize++;
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
                if (HiveRecordSource.ALIBABA_ACHIEVE.equals(source)) {
                    Duration duration = Duration.between(hiveRecord.getUpdateTime(), LocalDateTime.now());
                    hiveRecord.setDeletable(duration.toDays() > 61);
                } else {
                    hiveRecord.setDeletable(true);
                }
                hiveRecordRepository.saveAndFlush(hiveRecord);
            }
            log.info("{} actual size: {}", source, keyToObjectMap.size());
            log.info("{} uploaded size: {}", source, uploadedSize);
            log.info("{} oss only size: {}", source, ossOnlySize);
        } catch (Exception e) {
            log.error("sync {} failed", source, e);
        }
    }
}
