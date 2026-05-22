package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DbBackupManifestService {
    public static final String DB_BACKUP_KEY_PREFIX = "db-backup/";
    public static final String MANIFEST_SUFFIX = ".manifest.json";

    public String manifestKey(String batchId) {
        return DB_BACKUP_KEY_PREFIX + batchId + MANIFEST_SUFFIX;
    }

    public String manifestFileName(String batchId) {
        return batchId + MANIFEST_SUFFIX;
    }

    public List<HiveOssObject> listManifestObjects(List<HiveOssObject> objects) {
        List<HiveOssObject> manifests = new ArrayList<>();
        for (HiveOssObject object : objects) {
            String key = object.getFileKey();
            if (key != null && key.startsWith(DB_BACKUP_KEY_PREFIX) && key.endsWith(MANIFEST_SUFFIX)) {
                manifests.add(object);
            }
        }
        return manifests;
    }

    public List<HiveOssObject> listManifestObjectsFromOss(cc.cc3c.hive.oss.vendor.HiveOss oss, String backupBucket) throws Exception {
        HiveOssTask listTask = HiveOssTask.createTask()
                .withBucket(backupBucket)
                .withKey(DB_BACKUP_KEY_PREFIX);
        return listManifestObjects(oss.listObjects(listTask));
    }

    static String deriveBatchIdFromManifestKey(String manifestKey) {
        if (isBlank(manifestKey)) {
            return manifestKey;
        }
        String key = manifestKey;
        if (key.startsWith(DB_BACKUP_KEY_PREFIX)) {
            key = key.substring(DB_BACKUP_KEY_PREFIX.length());
        }
        if (key.endsWith(MANIFEST_SUFFIX)) {
            return key.substring(0, key.length() - MANIFEST_SUFFIX.length());
        }
        return key;
    }

    static String fileNameFromKey(String key) {
        if (key == null) {
            return null;
        }
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    public static String databaseFromBatchId(String batchId) {
        if (batchId == null || batchId.isEmpty()) {
            return batchId;
        }
        if (batchId.matches(".*-\\d{14}$")) {
            return batchId.substring(0, batchId.length() - 15);
        }
        return batchId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
