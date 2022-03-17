package cc.cc3c.hive.sync;

import cc.cc3c.hive.oss.HiveOssService;
import cc.cc3c.hive.oss.vo.HiveOssDownloadTask;
import cc.cc3c.hive.oss.vo.HiveOssTask;
import cc.cc3c.hive.repository.Record;
import cc.cc3c.hive.repository.RecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class HiveDownloadListener implements FileAlterationListener {
    @Value("${hive.sync.downloadDir}")
    private String downloadDir;

    @Autowired
    private HiveOssService hiveOssService;
    @Autowired
    private RecordRepository recordRepository;

    @Override
    public void onStart(FileAlterationObserver observer) {

    }

    @Override
    public void onDirectoryCreate(File directory) {

    }

    @Override
    public void onDirectoryChange(File directory) {

    }

    @Override
    public void onDirectoryDelete(File directory) {

    }

    @Override
    public void onFileCreate(File file) {
        Record record = null;
        try {
            String hiveFileName = file.getName();
            String idString = hiveFileName.substring(0, hiveFileName.length() - ".hive".length());
            int id = Integer.parseInt(idString);
            record = recordRepository.get(id);
            if (record == null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            log.error("fail to find record");
            return;
        }

        String recordFolder = downloadDir + "/" + record.getId() + "/";
        try {
            FileUtils.forceMkdir(new File(recordFolder));
        } catch (IOException e) {
            log.error("fail to create record folder", e);
            return;
        }

        File downloadFile;
        if (record.isZipped()) {
            downloadFile = new File(recordFolder + record.getFileName() + ".zip.hive");
        } else {
            downloadFile = new File(recordFolder + record.getFileName());
        }

        try {
            String source = record.getSource();
            if ("alibaba_achieve".equals(source)) {
                HiveOssDownloadTask task = HiveOssTask.createTask().withAlibabaAchieve()
                        .withEncryption(record.getFileName()).withKeyFile(record.getKey(), downloadFile)
                        .toDownloadTask();
                hiveOssService.alibabaOss().restore(task);
                hiveOssService.alibabaOss().download(task);
            } else if ("alibaba_standard".equals(source)) {
                HiveOssDownloadTask task = HiveOssTask.createTask().withAlibabaStandard()
                        .withEncryption(record.getFileName()).withKeyFile(record.getKey(), downloadFile)
                        .toDownloadTask();
                hiveOssService.alibabaOss().download(task);
            } else {
                log.error("unknown source {}", source);
            }
        } catch (Exception e) {
            log.error("{} download failed", record);
        }

//        if (recordPojo.getZipped()) {
//            File targetFolder = new File(downloadDir + recordPojo.getFileName());
//            FileUtils.forceMkdir(targetFolder);
//            ZipUtils.decompress(downloadFile, targetFolder);
//            FileUtils.deleteQuietly(downloadFile);
//        }
    }

    @Override
    public void onFileChange(File file) {

    }

    @Override
    public void onFileDelete(File file) {

    }

    @Override
    public void onStop(FileAlterationObserver observer) {

    }
}
