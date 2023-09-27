package cc.cc3c.hive.oss.vendor;

import cc.cc3c.hive.oss.vendor.vo.HiveOssDownloadTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssUploadTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.context.testng.AbstractTransactionalTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

@Slf4j
@SpringBootTest
@ActiveProfiles({"oss", "test"})
public class AlibabaOssTest extends AbstractTransactionalTestNGSpringContextTests {

    @Autowired
    HiveOssService hiveOssService;

    @Test
    public void alibabaStandardOss() throws Exception {
        File original = FileUtils.getFile("test.pdf");
        File downloaded = FileUtils.getFile("test.t.pdf");

        String key = System.currentTimeMillis() + "";
        HiveOssUploadTask uploadTask = HiveOssTask.createTask().withAlibabaStandard().withKeyFile(key, original).toUploadTask();
        hiveOssService.alibabaOss().upload(uploadTask);
        HiveOssDownloadTask downloadTask = HiveOssTask.createTask().withAlibabaStandard().withKeyFile(key, downloaded).toDownloadTask();
        hiveOssService.alibabaOss().download(downloadTask);

        String originalSha256 = DigestUtils.sha256Hex(FileUtils.openInputStream(original));
        String downloadedSha256 = DigestUtils.sha256Hex(FileUtils.openInputStream(downloaded));

        Assert.assertEquals(originalSha256, downloadedSha256);

        hiveOssService.alibabaOss().delete(downloadTask);
        FileUtils.deleteQuietly(downloaded);
    }

    @Test
    public void alibabaAchieveOss() throws Exception {

        File original = FileUtils.getFile("test.pdf");
        File downloaded = FileUtils.getFile("test.t.pdf");

        String key = System.currentTimeMillis() + "";
        HiveOssUploadTask uploadTask = HiveOssTask.createTask().withAlibabaAchieve().withKeyFile(key, original).toUploadTask();
        hiveOssService.alibabaOss().upload(uploadTask);

        HiveOssDownloadTask downloadTask = HiveOssTask.createTask().withAlibabaAchieve().withKeyFile(key, downloaded).toDownloadTask();

        hiveOssService.alibabaOss().restore(downloadTask);

        hiveOssService.alibabaOss().download(downloadTask);

        String originalSha256 = DigestUtils.sha256Hex(FileUtils.openInputStream(original));
        String downloadedSha256 = DigestUtils.sha256Hex(FileUtils.openInputStream(downloaded));

        Assert.assertEquals(originalSha256, downloadedSha256);
        FileUtils.deleteQuietly(downloaded);

    }
//
//    @Test
//    public void alibabaOssList() throws Exception {
//        HiveOssTask task = HiveOssTask.withTask().withAlibabaAchieve();
//        List<HiveOssObject> hiveOssObjectSummaries = hiveOssService.alibabaOss().listObjects(task);
//        for (HiveOssObject summary: hiveOssObjectSummaries){
//            log.info("{}",summary);
//        }
//    }
//    @Test
//    public void encryptionOss() throws Exception {
//
//        File original = FileUtils.getFile("test.pdf");
//        File downloaded = FileUtils.getFile("test.t.pdf");
//
//        HiveOssUploadTask uploadTask = HiveOssTask.withUploadTask().withTencent().withEncryption("fileName").withKeyFile("test.pdf", original).toUploadTask();
//        hiveOssService.tencentOss().upload(uploadTask);
//
//        HiveOssTask downloadTask = HiveOssTask.withTask().withTencent().withEncryption("fileName").withKeyFile("test.pdf", downloaded);
////        hiveOssService.tencentOss().downloadDecrypted(downloadTask);
//
//        String originalSha256 = DigestUtils.sha256Hex(FileUtils.openInputStream(original));
//        String downloadedSha256 = DigestUtils.sha256Hex(FileUtils.openInputStream(downloaded));
//
//        Assert.assertEquals(originalSha256, downloadedSha256);
//
//        hiveOssService.tencentOss().delete(downloadTask);
//    }

}