package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.oss.controller.vo.HiveSyncVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class HiveSyncSummaryService {

    private static final int MAX_MESSAGE_LENGTH = 512;

    private final FileCategoryRepository fileCategoryRepository;

    public HiveSyncSummaryService(FileCategoryRepository fileCategoryRepository) {
        this.fileCategoryRepository = fileCategoryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long categoryId, HiveSyncVO result) {
        FileCategoryEntity latest = resolveLatest(categoryId);
        latest.setLastSyncTime(result.getSyncedAt());
        latest.setLastSyncStatus(result.getStatus());
        latest.setLastSyncOssTotal(result.getOssTotal());
        latest.setLastSyncOssOnly(result.getOssOnly());
        latest.setLastSyncOssToDbMatched(result.getOssToDbMatched());
        latest.setLastSyncOssToDbMismatched(result.getOssToDbMismatched());
        latest.setLastSyncDbToOssMismatched(result.getDbToOssMismatched());
        latest.setLastSyncSkipped(result.getSkipped());
        latest.setLastSyncMessage(result.getMessage());
        fileCategoryRepository.save(latest);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long categoryId, Exception e) {
        FileCategoryEntity latest = resolveLatest(categoryId);
        latest.setLastSyncTime(LocalDateTime.now());
        latest.setLastSyncStatus("FAILED");
        latest.setLastSyncMessage(StringUtils.abbreviate(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), MAX_MESSAGE_LENGTH));
        fileCategoryRepository.save(latest);
    }

    private FileCategoryEntity resolveLatest(Long categoryId) {
        return fileCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryId));
    }
}
