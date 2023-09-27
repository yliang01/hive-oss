package cc.cc3c.hive.oss.vendor.vo;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class HiveOssUploadTask extends HiveOssTask {

    private String uploadId;
    private AtomicInteger currentPart = new AtomicInteger(0);

    private Map<Integer, String> uploadedMap = new ConcurrentHashMap<>();

    public HiveOssUploadTask(HiveOssTask hiveOssTask) {
        super(hiveOssTask);
    }
}

