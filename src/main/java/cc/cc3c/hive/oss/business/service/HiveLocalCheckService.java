package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveLocalCheckStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.business.web.CategoryContext;
import cc.cc3c.hive.oss.business.web.CategoryFileContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HiveLocalCheckService {

    private final HiveDownloadService hiveDownloadService;

    public HiveLocalCheckService(HiveDownloadService hiveDownloadService) {
        this.hiveDownloadService = hiveDownloadService;
    }

    public Map<Integer, LocalCheckResult> checkRecords(CategoryContext category, Collection<HiveRecord> records) {
        Map<Integer, LocalCheckResult> results = new LinkedHashMap<>();
        if (records == null || records.isEmpty()) {
            return results;
        }
        for (HiveRecord record : records) {
            if (HiveRecordStatus.UPLOADED.equals(record.getStatus())) {
                results.put(record.getId(), checkRecord(category, record));
            }
        }
        return results;
    }

    public LocalCheckResult checkRecord(CategoryContext category, HiveRecord record) {
        try {
            Path localFile = hiveDownloadService.getDownloadFile(CategoryFileContext.from(category, record)).toPath();
            if (!Files.exists(localFile, LinkOption.NOFOLLOW_LINKS)) {
                return LocalCheckResult.missing();
            }
            if (!Files.isRegularFile(localFile, LinkOption.NOFOLLOW_LINKS)) {
                return LocalCheckResult.unverifiable();
            }
            long observedSize = Files.size(localFile);
            if (record.getOriginalSize() == null) {
                return LocalCheckResult.baselineMissing(observedSize, localFile);
            }
            return record.getOriginalSize().longValue() == observedSize
                    ? LocalCheckResult.sizeMatched(observedSize, localFile)
                    : LocalCheckResult.sizeChanged(observedSize, localFile);
        } catch (NoSuchFileException e) {
            return LocalCheckResult.missing();
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            return LocalCheckResult.unverifiable();
        }
    }

    public record LocalCheckResult(HiveLocalCheckStatus status, Long observedSize, Path localFile) {
        private static LocalCheckResult sizeMatched(long observedSize, Path localFile) {
            return new LocalCheckResult(HiveLocalCheckStatus.SIZE_MATCHED, observedSize, localFile);
        }

        private static LocalCheckResult sizeChanged(long observedSize, Path localFile) {
            return new LocalCheckResult(HiveLocalCheckStatus.SIZE_CHANGED, observedSize, localFile);
        }

        private static LocalCheckResult missing() {
            return new LocalCheckResult(HiveLocalCheckStatus.MISSING, null, null);
        }

        private static LocalCheckResult baselineMissing(long observedSize, Path localFile) {
            return new LocalCheckResult(HiveLocalCheckStatus.BASELINE_MISSING, observedSize, localFile);
        }

        private static LocalCheckResult unverifiable() {
            return new LocalCheckResult(HiveLocalCheckStatus.UNVERIFIABLE, null, null);
        }
    }
}
