package cc.cc3c.hive.oss.web;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.model.HiveRestoreStatusState;
import cc.cc3c.hive.domain.model.HiveStorageProvider;

import java.time.LocalDateTime;

public record CategoryFileContext(
        CategoryContext category,
        String key,
        Integer recordId,
        String fileName,
        Boolean zipped,
        HiveStorageProvider provider,
        Long size,
        HiveRecordStatus status,
        LocalDateTime restoreTime,
        HiveRestoreStatusState restoreStatus,
        HiveDownloadStatus downloadStatus
) {
    public static CategoryFileContext from(CategoryContext category, HiveRecord record) {
        return new CategoryFileContext(category, record.getFileKey(), record.getId(), record.getFileName(), record.getZipped(),
                record.getProvider(), record.getSize(), record.getStatus(), record.getRestoreTime(),
                record.getRestoreStatus(), record.getDownloadStatus());
    }
}
