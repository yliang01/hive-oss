package cc.cc3c.hive.oss.sync;

import cc.cc3c.hive.oss.vendor.HiveOssService;
import cc.cc3c.hive.oss.record.repository.HiveRecord;
import cc.cc3c.hive.oss.record.repository.HiveRecordDbRepository;
import cc.cc3c.hive.oss.vendor.vo.HiveOssDownloadTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.record.HiveRecordSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
public class HiveDownloadListener implements FileAlterationListener {
    @Value("${hive.sync.downloadDir}")
    private String downloadDir;
    @Autowired
    private HiveRecordDbRepository hiveRecordDbRepository;
    @Autowired
    private HiveOssService hiveOssService;
    @Getter
    private File downloadFolder;

    @PostConstruct
    public void init() {
        downloadFolder = new File(downloadDir);
        try {
            FileUtils.forceMkdir(downloadFolder);
        } catch (IOException e) {
            log.error("fail to create download folder", e);
            System.exit(1);
        }
    }

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
        HiveRecord hiveRecord = null;
        try {
            String hiveFileName = file.getName();
            String idString = hiveFileName.substring(0, hiveFileName.length() - ".hive".length());
            hiveRecord = hiveRecordDbRepository.findById(Integer.parseInt(idString)).orElseThrow();
        } catch (Exception e) {
            log.error("fail to find record", e);
            return;
        }

        String recordFolder = downloadDir + "/" + hiveRecord.getId() + "/";
        try {
            FileUtils.forceMkdir(new File(recordFolder));
        } catch (IOException e) {
            log.error("fail to create record folder", e);
            return;
        }

        File downloadFile;
        if (hiveRecord.getZipped()) {
            downloadFile = new File(recordFolder + hiveRecord.getFileName() + ".zip");
        } else {
            downloadFile = new File(recordFolder + hiveRecord.getFileName());
        }

        try {
            HiveRecordSource source = hiveRecord.getSource();
            if (HiveRecordSource.ALIBABA_ACHIEVE.equals(source)) {
                HiveOssDownloadTask task = HiveOssTask.createTask().withAlibabaAchieve()
                        .withEncryption(hiveRecord.getFileName()).withKeyFile(hiveRecord.getFileKey(), downloadFile)
                        .toDownloadTask();
                hiveOssService.alibabaOss().restore(task);
                hiveOssService.alibabaOss().download(task);
            } else if (HiveRecordSource.ALIBABA_STANDARD.equals(source)) {
                HiveOssDownloadTask task = HiveOssTask.createTask().withAlibabaStandard()
                        .withEncryption(hiveRecord.getFileName()).withKeyFile(hiveRecord.getFileKey(), downloadFile)
                        .toDownloadTask();
                hiveOssService.alibabaOss().download(task);
            } else {
                log.error("unknown source {}", source);
            }
        } catch (Exception e) {
            log.error("{} download failed", hiveRecord);
        }
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
