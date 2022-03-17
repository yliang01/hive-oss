package cc.cc3c.hive.sync;

import cc.cc3c.hive.oss.HiveOssService;
import cc.cc3c.hive.oss.vo.HiveOssTask;
import cc.cc3c.hive.oss.vo.HiveOssUploadTask;
import cc.cc3c.hive.repository.Record;
import cc.cc3c.hive.repository.RecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
public class HiveUploadListener implements FileAlterationListener {

    @Autowired
    private HiveOssService hiveOssService;
    @Autowired
    private RecordRepository recordRepository;

    @Override
    public void onStart(FileAlterationObserver fileAlterationObserver) {

    }

    @Override
    public void onDirectoryCreate(File file) {
//        try {
//            File compressedFile = ZipUtils.compress(file);
//            String fileName = file.getName();
//            String key = DigestUtils.md5Hex(file.getName());
//
//            HiveOssUploadTask task = HiveOssTask.createTask().withAlibabaStandard().withEncryption(fileName).withKeyFile(key, compressedFile).toUploadTask();
//            hiveOssService.alibabaOss().upload(task);
//
//            FileUtils.deleteQuietly(file);
//            FileUtils.deleteQuietly(compressedFile);
//        } catch (Exception e) {
//            log.error("onDirectoryCreate", e);
//        }
    }

    @Override
    public void onDirectoryChange(File file) {

    }

    @Override
    public void onDirectoryDelete(File file) {

    }

    @Override
    public void onFileCreate(File file) {
        try {
            String path = file.getCanonicalPath();
            String fileName = file.getName();
            String key = DigestUtils.md5Hex(file.getName());

            HiveOssUploadTask task = null;
            String source = null;
            if (path.contains("alibaba_standard")) {
                task = HiveOssTask.createTask().withAlibabaStandard()
                        .withEncryption(fileName).withKeyFile(key, file)
                        .toUploadTask();
                source = "alibaba_standard";
            } else if (path.contains("alibaba_achieve")) {
                task = HiveOssTask.createTask().withAlibabaAchieve()
                        .withEncryption(fileName).withKeyFile(key, file)
                        .toUploadTask();
                source = "alibaba_achieve";
            } else {
                log.error("unknown storage type");
                return;
            }

            hiveOssService.alibabaOss().upload(task);
            Record record = new Record();
            record.setFileName(fileName);
            record.setKey(key);
            record.setZipped(false);
            record.setSource(source);
            recordRepository.put(record);
            FileUtils.deleteQuietly(file);
        } catch (Exception e) {
            log.error("onFileCreate", e);
        }
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
