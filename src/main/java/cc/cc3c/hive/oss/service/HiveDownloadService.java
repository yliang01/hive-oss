package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HiveDownloadService {

    private final HiveOssService hiveOssService;

    private final HiveRecordRepository hiveRecordRepository;

    public HiveDownloadService(HiveRecordRepository hiveRecordRepository, HiveOssService hiveOssService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveOssService = hiveOssService;
    }

    private String downloadDir;

    private File downloadFolder;

    @Value("${hive.downloadDir}")
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

    private final ConcurrentHashMap<String, HiveOssTask> downloadTasks = new ConcurrentHashMap<>();

    public HiveOssTask getDownLoadTask(String fileKey) {
        return downloadTasks.get(fileKey);
    }

    public void download(HiveRecord hiveRecord) throws Exception {
        if (HiveDownloadStatus.downloading == hiveRecord.getDownloadStatus()) {
            return;
        }
        HiveOssTask task = HiveOssTask.createTask()
                .withBucket(hiveRecord.getSource())
                .withKey(hiveRecord.getFileKey())
                .withOutputStream(new FileOutputStream(getDownloadFile(hiveRecord)));
        if (!HiveRecordStatus.OSS_ONLY.equals(hiveRecord.getStatus())) {
            task = task.withEncryption(hiveRecord.getFileName());
        }
        log.info("download start {}", task);
        hiveRecordRepository.updateDownloadStatus(task.getKey(), HiveDownloadStatus.downloading);
        downloadTasks.put(task.getKey(), task);
        HiveOssTask finalTask = task;
        Thread.ofVirtual().start(() -> {
            try {
                hiveOssService.using(hiveRecord.getSource()).download(finalTask);
                hiveRecordRepository.updateDownloadStatus(finalTask.getKey(), HiveDownloadStatus.success);
                log.info("download finished {}", finalTask);
            } catch (Exception e) {
                log.error("download failed {}", finalTask, e);
                hiveRecordRepository.updateDownloadStatus(finalTask.getKey(), HiveDownloadStatus.failed);
            } finally {
                downloadTasks.remove(finalTask.getKey());
            }
        });
    }

    public File getDownloadFile(HiveRecord hiveRecord) {
        String fileName;
        if (StringUtils.isNotEmpty(hiveRecord.getFileName())) {
            fileName = hiveRecord.getFileName();
        } else {
            fileName = hiveRecord.getFileKey();
        }
        return new File(downloadFolder, hiveRecord.getZipped() ? fileName + ".zip" : fileName);
    }
}