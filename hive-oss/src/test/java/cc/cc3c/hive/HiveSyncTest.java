//package cc.cc3c.hive;
//
//import cc.cc3c.hive.jooq.tables.daos.HiveRecordDao;
//import cc.cc3c.hive.jooq.tables.pojos.HiveRecordPojo;
//import cc.cc3c.hive.oss.HiveOssObject;
//import cc.cc3c.hive.oss.HiveOssService;
//import cc.cc3c.hive.oss.task.HiveOssTask;
//import cc.cc3c.hive.oss.task.HiveOssUploadTask;
//import cc.cc3c.hive.oss.util.ZipUtils;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.codec.digest.DigestUtils;
//import org.apache.commons.io.FileUtils;
//import org.junit.Assert;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.SpringBootConfiguration;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import java.io.File;
//
//
//@Slf4j
//@RunWith(SpringRunner.class)
//@SpringBootApplication
//@SpringBootTest
//@ActiveProfiles({"domain", "oss", "test"})
//public class HiveSyncTest {
//
//    @Value("${hive.worker.downloadDir}")
//    private String downloadDir;
//
//    @Autowired
//    HiveOssService hiveOssService;
//
//    @Autowired
//    HiveRecordDao hiveRecordDao;
//
//    @Test
//    public void a() throws Exception {
//
//        HiveRecordPojo recordPojo = hiveRecordDao.fetchOneByKey("5a105e8b9d40e1329780d62ea2265d8a");
//
//        File downloadFile;
//        if (recordPojo.getZipped()) {
//            downloadFile = new File(downloadDir + recordPojo.getFileName() + ".zip.hive");
//        } else {
//            downloadFile = new File(downloadDir + recordPojo.getFileName());
//        }
//
//        HiveOssTask task = HiveOssTask.withTask().withAlibabaAchieve().withEncryption(recordPojo.getFileName()).withKeyFile(recordPojo.getKey(), downloadFile);
//
//        hiveOssService.alibabaOss().restore(task);
//        while (!(hiveOssService.alibabaOss().isRestored(task))) {
//            Thread.sleep(5000);
//        }
//
//        hiveOssService.alibabaOss().asyncDownloadDecrypted(task, () -> {
//            if (recordPojo.getZipped()) {
//                File targetFolder = new File(downloadDir + recordPojo.getFileName());
//                FileUtils.forceMkdir(targetFolder);
//                ZipUtils.decompress(downloadFile, targetFolder);
//                FileUtils.deleteQuietly(downloadFile);
//            }
//        });
//
//        Thread.sleep(1000000);
//    }
//}