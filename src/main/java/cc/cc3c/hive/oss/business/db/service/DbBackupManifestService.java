package cc.cc3c.hive.oss.business.db.service;

import cc.cc3c.hive.oss.business.vendor.client.vo.HiveOssObject;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DbBackupManifestService {
    public static final String DB_BACKUP_KEY_PREFIX = "db-backup/";
    public static final String MANIFEST_SUFFIX = ".manifest.json";
    private static final DateTimeFormatter BATCH_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("uuuuMMddHHmmss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

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

    public static String databaseFromBatchId(String batchId) {
        if (batchId == null || batchId.isEmpty()) {
            return batchId;
        }
        if (batchId.matches(".*-\\d{14}$")) {
            return batchId.substring(0, batchId.length() - 15);
        }
        return batchId;
    }

    public static Optional<Instant> backupTimeFromBatchId(String batchId) {
        if (batchId == null) {
            return Optional.empty();
        }
        int separator = batchId.lastIndexOf('-');
        if (separator < 0) {
            return Optional.empty();
        }
        String timestamp = batchId.substring(separator + 1);
        if (!timestamp.matches("\\d{14}")) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(timestamp, BATCH_TIMESTAMP_FORMAT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
