package cc.cc3c.hive.oss.sync;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.vendor.HiveOssService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssDownloadTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class HiveDownloader {
    @Autowired
    HiveRecordRepository hiveRecordRepository;
    @Autowired
    HiveOssService hiveOssService;

    @Value("${hive.sync.downloadDir}")
    private String downloadDir;
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

    @EventListener(classes = ApplicationReadyEvent.class)
    public void syncRecord() {
        download("");
    }

    public void download(String fileKey) {
        try {
            Optional<HiveRecord> hiveRecordOptional = hiveRecordRepository.findByFileKey(fileKey);
            if (hiveRecordOptional.isPresent()) {
                HiveRecord hiveRecord = hiveRecordOptional.get();
                File downloadFile = new File(downloadDir, hiveRecord.getZipped() ? hiveRecord.getFileKey() + ".zip" : hiveRecord.getFileKey());
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
                    log.error("unknown source {} for fileKey {}", source, fileKey);
                }
            } else {
                log.error("unknown fileKey {}", fileKey);
            }
        } catch (Exception e) {
            log.error("download failed for fileKey {}", fileKey);
        }
    }
}
