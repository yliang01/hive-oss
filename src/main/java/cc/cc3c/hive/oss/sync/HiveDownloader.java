package cc.cc3c.hive.oss.sync;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class HiveDownloader {

    private final HiveOssService hiveOssService;

    private final HiveRecordRepository hiveRecordRepository;

    public HiveDownloader(HiveRecordRepository hiveRecordRepository, HiveOssService hiveOssService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveOssService = hiveOssService;
    }

    private String downloadDir;

    private File downloadFolder;

    @Value("${hive.sync.downloadDir}")
    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }

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
                File downloadFile = new File(downloadFolder, hiveRecord.getZipped() ? hiveRecord.getFileKey() + ".zip" : hiveRecord.getFileKey());
                HiveRecordSource source = hiveRecord.getSource();
                if (HiveRecordSource.ALIBABA_ACHIEVE.equals(source)) {
                    HiveOssTask task = HiveOssTask.createTask().withBucket(source)
                            .withEncryption(hiveRecord.getFileName()).withKeyFile(hiveRecord.getFileKey(), downloadFile);
                    hiveOssService.using(source).restore(task);
                    hiveOssService.using(source).download(task);
                } else if (HiveRecordSource.ALIBABA_STANDARD.equals(source)) {
                    HiveOssTask task = HiveOssTask.createTask().withBucket(source)
                            .withEncryption(hiveRecord.getFileName()).withKeyFile(hiveRecord.getFileKey(), downloadFile) ;
                    hiveOssService.using(source).download(task);
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
