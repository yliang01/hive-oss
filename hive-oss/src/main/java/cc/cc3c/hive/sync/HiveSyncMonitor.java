package cc.cc3c.hive.sync;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class HiveSyncMonitor {

    @Value("${hive.sync.uploadDir}")
    private String uploadDir;
    @Value("${hive.sync.downloadDir}")
    private String downloadDir;

    @Autowired
    private HiveUploadListener hiveUploadListener;
    @Autowired
    private HiveDownloadListener hiveDownloadListener;

    private File uploadFolder = null;
    private File alibabaStandardFolder = null;
    private File alibabaAchieveFolder = null;

    private File downloadFolder = null;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReadyEvent() {
        uploadFolder = new File(uploadDir);
        alibabaStandardFolder = new File(uploadDir + "alibaba_standard/");
        alibabaAchieveFolder = new File(uploadDir + "alibaba_achieve/");
        try {
            FileUtils.forceMkdir(alibabaStandardFolder);
            FileUtils.forceMkdir(alibabaStandardFolder);
        } catch (IOException e) {
            log.error("fail to create upload folder", e);
            System.exit(1);
        }

        downloadFolder = new File(downloadDir);
        try {
            FileUtils.forceMkdir(downloadFolder);
        } catch (IOException e) {
            log.error("fail to create download folder", e);
            System.exit(1);
        }

        try {
            FileAlterationMonitor uploadMonitor = new FileAlterationMonitor();
            FileAlterationObserver alibabaStandardObserver = new FileAlterationObserver(alibabaStandardFolder, (file) -> {
                return !file.getName().endsWith(".hive");
            });
            alibabaStandardObserver.addListener(hiveUploadListener);
            FileAlterationObserver alibabaAchieveObserver = new FileAlterationObserver(alibabaAchieveFolder, (file) -> {
                return !file.getName().endsWith(".hive");
            });
            alibabaAchieveObserver.addListener(hiveUploadListener);
            uploadMonitor.addObserver(alibabaStandardObserver);
            uploadMonitor.addObserver(alibabaAchieveObserver);
            uploadMonitor.start();
        } catch (Exception e) {
            log.error("failed to start upload monitor", e);
        }

        try {
            FileAlterationMonitor downloadMonitor = new FileAlterationMonitor();
            FileAlterationObserver downloadObserver = new FileAlterationObserver(downloadFolder, (file) -> {
                return file.isFile() && file.getName().endsWith(".hive");
            });
            downloadObserver.addListener(hiveDownloadListener);
            downloadMonitor.addObserver(downloadObserver);
            downloadMonitor.start();
        } catch (Exception e) {
            log.error("failed to start download monitor", e);
        }
    }
}
