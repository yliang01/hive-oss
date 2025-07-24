package cc.cc3c.hive.oss.sync;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HiveSyncMonitor {
    @Autowired
    private HiveUploader hiveUploader;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReadyEvent() {
        try {
            FileAlterationMonitor uploadMonitor = new FileAlterationMonitor();
            FileAlterationObserver alibabaStandardObserver = new FileAlterationObserver(hiveUploader.getAlibabaStandardFolder(), (file) -> {
                return !file.getName().endsWith(".hive");
            });
            alibabaStandardObserver.addListener(hiveUploader);
            FileAlterationObserver alibabaAchieveObserver = new FileAlterationObserver(hiveUploader.getAlibabaAchieveFolder(), (file) -> {
                return !file.getName().endsWith(".hive");
            });
            alibabaAchieveObserver.addListener(hiveUploader);
            uploadMonitor.addObserver(alibabaStandardObserver);
            uploadMonitor.addObserver(alibabaAchieveObserver);
            uploadMonitor.start();
        } catch (Exception e) {
            log.error("failed to start upload monitor", e);
        }
    }
}
