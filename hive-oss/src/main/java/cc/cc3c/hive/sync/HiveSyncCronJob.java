//package cc.cc3c.hive.sync.monitor;
//
//import cc.cc3c.hive.jooq.tables.daos.HiveRecordDao;
//import cc.cc3c.hive.jooq.tables.daos.HiveRestoreDao;
//import cc.cc3c.hive.jooq.tables.pojos.HiveRecordPojo;
//import cc.cc3c.hive.oss.HiveOssObject;
//import cc.cc3c.hive.oss.HiveOssService;
//import cc.cc3c.hive.oss.task.HiveOssTask;
//import cc.cc3c.hive.oss.util.ZipUtils;
//import cc.cc3c.hive.sync.repository.HiveRecordRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.io.FileUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//@EnableScheduling
//public class HiveSyncCronJob {
//
//
////
////    @Scheduled(cron = "0/10 * * * * ?")
////    public void run() {
////        try {
////            System.out.println("cron");
////            List<HiveRecordPojo> pendingRestoreRecords = hiveWebRepository.getPendingRestoreRecords();
////            for (HiveRecordPojo hiveRecordPojo : pendingRestoreRecords) {
////                if (HiveOss.getAlibabaOss().isRestored(hiveRecordPojo.getKey())) {
////
////
////                }
////            }
////        } catch (Exception e) {
////            e.printStackTrace();
////        }
////    }
//
////        @Scheduled(cron = "0/10 * * * * ?")
//    //    @Scheduled(cron = "0 0 0 1/1 * ?")
//    public void syncRecord() {
//        try {
//
//
////            for (HiveOssObject ossObjectSummary : objectSummaryList) {
////                if (keyToRecordPojoMap.containsKey(ossObjectSummary.getKey())) {
////                    HiveRecordPojo hiveRecordPojo = keyToRecordPojoMap.get(ossObjectSummary.getKey());
////                    hiveRecordPojo.setSize(ossObjectSummary.getSize());
////                    hiveRecordPojo.setUpdateTime(new Timestamp(ossObjectSummary.getLastModified().getTime()));
////                    hiveRecordDao.update(hiveRecordPojo);
////                } else {
////                    System.out.println(ossObjectSummary);
////                }
////            }
//
//
//            Map<String, HiveRecordPojo> keyToRecordMap = hiveRecordRepository.getKeyToRecordMap();
//
//            Map<String, HiveOssObject> keyToObjectMap = hiveOssService.alibabaOss().listObjects(HiveOssTask.withTask().withAlibabaAchieve()).stream().collect(Collectors.toMap(HiveOssObject::getKey, v -> v));
//
//
//            log.info("not exist in oss");
//            for (Map.Entry<String, HiveRecordPojo> entry : keyToRecordMap.entrySet()) {
//                if (!keyToObjectMap.containsKey(entry.getKey())) {
//                    log.info("{}", entry.getValue());
//                }
//            }
//
//            log.info("not exist in record");
//            for (Map.Entry<String, HiveOssObject> entry : keyToObjectMap.entrySet()) {
//                if (!keyToRecordMap.containsKey(entry.getKey())) {
//                    log.info("{}", entry.getValue());
//                }
//            }
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
