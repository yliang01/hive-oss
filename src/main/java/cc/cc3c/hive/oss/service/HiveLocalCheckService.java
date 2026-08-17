package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveLocalCheckStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class HiveLocalCheckService {

    @Value("${hive.localScanDir}")
    private String localScanDir;

    public Map<Integer, LocalCheckResult> checkRecords(String categoryCode, Collection<HiveRecord> records) {
        Map<Integer, LocalCheckResult> results = new LinkedHashMap<>();
        if (records == null || records.isEmpty()) {
            return results;
        }
        Path categoryDirectory;
        try {
            categoryDirectory = resolveCategoryDirectory(categoryCode);
        } catch (IOException | RuntimeException e) {
            log.warn("local check directory unavailable for {}: {}", categoryCode, e.getMessage());
            for (HiveRecord record : records) {
                if (HiveRecordStatus.UPLOADED.equals(record.getStatus())) {
                    results.put(record.getId(), LocalCheckResult.unverifiable());
                }
            }
            return results;
        }
        for (HiveRecord record : records) {
            if (HiveRecordStatus.UPLOADED.equals(record.getStatus())) {
                results.put(record.getId(), checkRecord(categoryDirectory, record));
            }
        }
        return results;
    }

    public LocalCheckResult checkRecord(String categoryCode, HiveRecord record) {
        return checkRecords(categoryCode, java.util.List.of(record)).get(record.getId());
    }

    public boolean hasLocalSourceOrIsUnverifiable(String categoryCode, HiveRecord record) {
        if (!HiveRecordStatus.UPLOADED.equals(record.getStatus())) {
            return false;
        }
        LocalCheckResult result = checkRecord(categoryCode, record);
        return result != null && result.status() != HiveLocalCheckStatus.MISSING;
    }

    private LocalCheckResult checkRecord(Path categoryDirectory, HiveRecord record) {
        try {
            String groupCode = record.getGroup() == null ? null : record.getGroup().getGroupCode();
            Path localFile = resolveExistingLocalFile(categoryDirectory, record.getFileName(), groupCode);
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

    private Path resolveCategoryDirectory(String categoryCode) throws IOException {
        if (!isSafePathSegment(categoryCode)) {
            throw new IllegalArgumentException("分类编码不能用于本地检查目录: " + categoryCode);
        }
        Path configuredRoot = Paths.get(localScanDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(configuredRoot)) {
            throw new IllegalArgumentException("本地检查根目录不存在或不是目录: " + configuredRoot);
        }
        Path root = configuredRoot.toRealPath();
        Path categoryDirectory = root.resolve(categoryCode).normalize();
        if (!categoryDirectory.startsWith(root)) {
            throw new IllegalArgumentException("分类检查目录超出允许范围: " + categoryCode);
        }
        if (Files.isSymbolicLink(categoryDirectory)
                || !Files.isDirectory(categoryDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("本地分类检查目录不存在、不是目录或为符号链接: " + categoryDirectory);
        }
        Path resolvedCategoryDirectory = categoryDirectory.toRealPath();
        if (!resolvedCategoryDirectory.startsWith(root)) {
            throw new IllegalArgumentException("分类检查目录超出允许范围: " + categoryCode);
        }
        return resolvedCategoryDirectory;
    }

    private Path resolveExistingLocalFile(Path categoryDirectory, String fileName, String groupCode) throws IOException {
        if (!isSafePathSegment(fileName)) {
            throw new IllegalArgumentException("文件名不能用于本地检查路径: " + fileName);
        }
        Path parentDirectory = categoryDirectory;
        if (groupCode != null) {
            if (!isSafePathSegment(groupCode)) {
                throw new IllegalArgumentException("分组编码不能用于本地检查路径: " + groupCode);
            }
            Path groupDirectory = categoryDirectory.resolve(groupCode).normalize();
            if (!groupDirectory.startsWith(categoryDirectory)) {
                throw new IllegalArgumentException("分组目录超出允许范围: " + groupCode);
            }
            if (!Files.exists(groupDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new NoSuchFileException(groupDirectory.toString());
            }
            if (Files.isSymbolicLink(groupDirectory)
                    || !Files.isDirectory(groupDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("分组目录不是安全目录: " + groupDirectory);
            }
            Path resolvedGroupDirectory = groupDirectory.toRealPath();
            if (!resolvedGroupDirectory.startsWith(categoryDirectory)) {
                throw new IllegalArgumentException("分组目录超出允许范围: " + groupCode);
            }
            parentDirectory = resolvedGroupDirectory;
        }
        Path localFile = parentDirectory.resolve(fileName).normalize();
        if (!localFile.startsWith(parentDirectory)) {
            throw new IllegalArgumentException("文件路径超出允许范围: " + fileName);
        }
        if (!Files.exists(localFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(localFile.toString());
        }
        if (Files.isSymbolicLink(localFile) || !Files.isRegularFile(localFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("本地文件不是安全普通文件: " + localFile);
        }
        return localFile;
    }

    private boolean isSafePathSegment(String value) {
        if (value == null || value.isBlank() || ".".equals(value) || "..".equals(value)) {
            return false;
        }
        try {
            Path path = Paths.get(value);
            return !path.isAbsolute() && path.getNameCount() == 1 && value.equals(path.getFileName().toString());
        } catch (InvalidPathException e) {
            return false;
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
