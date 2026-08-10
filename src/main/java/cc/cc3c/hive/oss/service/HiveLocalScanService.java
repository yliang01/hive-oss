package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveLocalCheckStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveLocalScanVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HiveLocalScanService {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveLocalScanSummaryService summaryService;
    private final ConcurrentHashMap<Long, ReentrantLock> scanLocks = new ConcurrentHashMap<>();

    @Value("${hive.localScanDir}")
    private String localScanDir;

    public HiveLocalScanService(HiveRecordRepository hiveRecordRepository,
                                 HiveLocalScanSummaryService summaryService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.summaryService = summaryService;
    }

    public HiveLocalScanVO scanLocal(Long categoryId, String categoryCode) throws IOException {
        ReentrantLock lock = scanLocks.computeIfAbsent(categoryId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new LocalScanAlreadyRunningException(categoryCode);
        }
        try {
            Path categoryDirectory = resolveCategoryDirectory(categoryCode);
            List<HiveRecord> records = hiveRecordRepository.findByCategory_IdAndStatusAndDeletedIsFalse(categoryId, HiveRecordStatus.UPLOADED);
            Map<Integer, String> groupCodeByRecordId = findRecordGroupCodes(records);
            LocalDateTime checkedAt = LocalDateTime.now();
            LocalCheckCounters counters = new LocalCheckCounters();
            for (HiveRecord record : records) {
                LocalCheckObservation observation = checkRecord(categoryDirectory, record, groupCodeByRecordId.get(record.getId()));
                if (hiveRecordRepository.updateLocalCheck(record.getId(), HiveRecordStatus.UPLOADED,
                        observation.status(), checkedAt, observation.observedSize()) == 1) {
                    counters.add(observation.status());
                }
            }
            HiveLocalScanVO result = HiveLocalScanVO.builder()
                    .status("SUCCESS")
                    .checkedAt(checkedAt)
                    .recordTotal(counters.recordTotal)
                    .sizeMatched(counters.sizeMatched)
                    .sizeChanged(counters.sizeChanged)
                    .missing(counters.missing)
                    .baselineMissing(counters.baselineMissing)
                    .unverifiable(counters.unverifiable)
                    .build();
            summaryService.markSuccess(categoryId, result);
            return result;
        } catch (IOException | RuntimeException e) {
            summaryService.markFailed(categoryId, e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public Path resolveExistingLocalFile(String categoryCode, String fileName, String groupCode) throws IOException {
        return resolveExistingLocalFile(resolveCategoryDirectory(categoryCode), fileName, groupCode);
    }

    private LocalCheckObservation checkRecord(Path categoryDirectory, HiveRecord record, String groupCode) {
        try {
            Path localFile = resolveExistingLocalFile(categoryDirectory, record.getFileName(), groupCode);
            long observedSize = Files.size(localFile);
            if (record.getOriginalSize() == null) {
                return LocalCheckObservation.baselineMissing(observedSize);
            }
            return record.getOriginalSize().longValue() == observedSize
                    ? LocalCheckObservation.sizeMatched(observedSize)
                    : LocalCheckObservation.sizeChanged(observedSize);
        } catch (NoSuchFileException e) {
            return LocalCheckObservation.missing();
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            return LocalCheckObservation.unverifiable();
        }
    }

    private Map<Integer, String> findRecordGroupCodes(Collection<HiveRecord> records) {
        List<Integer> recordIds = records.stream().map(HiveRecord::getId).filter(id -> id != null).toList();
        Map<Integer, String> groupCodeByRecordId = new HashMap<>();
        if (recordIds.isEmpty()) {
            return groupCodeByRecordId;
        }
        for (HiveRecord record : hiveRecordRepository.findWithGroupByIdIn(recordIds)) {
            if (record.getGroup() != null) {
                groupCodeByRecordId.put(record.getId(), record.getGroup().getGroupCode());
            }
        }
        return groupCodeByRecordId;
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

    private record LocalCheckObservation(HiveLocalCheckStatus status, Long observedSize) {
        private static LocalCheckObservation sizeMatched(long observedSize) {
            return new LocalCheckObservation(HiveLocalCheckStatus.SIZE_MATCHED, observedSize);
        }

        private static LocalCheckObservation sizeChanged(long observedSize) {
            return new LocalCheckObservation(HiveLocalCheckStatus.SIZE_CHANGED, observedSize);
        }

        private static LocalCheckObservation missing() {
            return new LocalCheckObservation(HiveLocalCheckStatus.MISSING, null);
        }

        private static LocalCheckObservation baselineMissing(long observedSize) {
            return new LocalCheckObservation(HiveLocalCheckStatus.BASELINE_MISSING, observedSize);
        }

        private static LocalCheckObservation unverifiable() {
            return new LocalCheckObservation(HiveLocalCheckStatus.UNVERIFIABLE, null);
        }
    }

    private static class LocalCheckCounters {
        private int recordTotal;
        private int sizeMatched;
        private int sizeChanged;
        private int missing;
        private int baselineMissing;
        private int unverifiable;

        private void add(HiveLocalCheckStatus status) {
            recordTotal++;
            switch (status) {
                case SIZE_MATCHED -> sizeMatched++;
                case SIZE_CHANGED -> sizeChanged++;
                case MISSING -> missing++;
                case BASELINE_MISSING -> baselineMissing++;
                case UNVERIFIABLE -> unverifiable++;
            }
        }
    }
}
