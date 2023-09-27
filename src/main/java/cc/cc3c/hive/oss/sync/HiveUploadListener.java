package cc.cc3c.hive.oss.sync;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.vendor.HiveOssService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssUploadTask;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class HiveUploadListener implements FileAlterationListener {
    @Value("${hive.sync.uploadDir}")
    private String uploadDir;
    @Autowired
    private HiveOssService hiveOssService;
    @Getter
    private File alibabaStandardFolder;
    @Getter
    private File alibabaAchieveFolder;

    @PostConstruct
    public void init() {
        alibabaStandardFolder = new File(uploadDir + HiveRecordSource.ALIBABA_STANDARD);
        alibabaAchieveFolder = new File(uploadDir + HiveRecordSource.ALIBABA_ACHIEVE);
        try {
            FileUtils.forceMkdir(alibabaStandardFolder);
            FileUtils.forceMkdir(alibabaAchieveFolder);
        } catch (IOException e) {
            log.error("fail to create upload folder", e);
            System.exit(1);
        }
    }

    @Override
    public void onStart(FileAlterationObserver fileAlterationObserver) {

    }

    @Override
    public void onDirectoryCreate(File file) {
    }

    @Override
    public void onDirectoryChange(File file) {

    }

    @Override
    public void onDirectoryDelete(File file) {

    }

    @Override
    public void onFileCreate(File file) {
//        try {
//            String fileName = file.getName();
//            String fileKey = DigestUtils.md5Hex(file.getName());
//            HiveOssUploadTask task;
//            HiveRecordSource source;
//            if (file.getCanonicalPath().contains(HiveRecordSource.ALIBABA_STANDARD.name())) {
//                task = HiveOssTask.createTask().withAlibabaStandard()
//                        .withEncryption(fileName).withKeyFile(fileKey, file)
//                        .toUploadTask();
//                source = HiveRecordSource.ALIBABA_STANDARD;
//            } else if (file.getCanonicalPath().contains(HiveRecordSource.ALIBABA_ACHIEVE.name())) {
//                task = HiveOssTask.createTask().withAlibabaAchieve()
//                        .withEncryption(fileName).withKeyFile(fileKey, file)
//                        .toUploadTask();
//                source = HiveRecordSource.ALIBABA_ACHIEVE;
//            } else {
//                log.error("unknown storage type");
//                return;
//            }
//
//            HiveRecord hiveRecord = new HiveRecord();
//            hiveRecord.setFileName(fileName);
//            hiveRecord.setFileKey(fileKey);
//            hiveRecord.setZipped(false);
//            hiveRecord.setSource(source);
//            hiveRecord.setStatus(HiveRecordStatus.UPLOADING);
//            hiveRecordDbRepository.save(hiveRecord);
//
//            hiveOssService.alibabaOss().upload(task);
//
//            hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
//            hiveRecordDbRepository.save(hiveRecord);
//            FileUtils.deleteQuietly(file);
//        } catch (Exception e) {
//            log.error("onFileCreate", e);
//        }
    }

    @Override
    public void onFileChange(File file) {

    }

    @Override
    public void onFileDelete(File file) {

    }

    @Override
    public void onStop(FileAlterationObserver fileAlterationObserver) {

    }
}
