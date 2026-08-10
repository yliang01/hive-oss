package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.oss.controller.vo.HiveLocalScanVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class HiveLocalScanSummaryService {

    private static final int MAX_MESSAGE_LENGTH = 512;

    private final FileCategoryRepository fileCategoryRepository;

    public HiveLocalScanSummaryService(FileCategoryRepository fileCategoryRepository) {
        this.fileCategoryRepository = fileCategoryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long categoryId, HiveLocalScanVO result) {
        FileCategoryEntity latest = resolveLatest(categoryId);
        latest.setLastLocalCheckTime(result.getCheckedAt());
        latest.setLastLocalCheckStatus(result.getStatus());
        latest.setLastLocalCheckRecordTotal(result.getRecordTotal());
        latest.setLastLocalCheckSizeMatched(result.getSizeMatched());
        latest.setLastLocalCheckSizeChanged(result.getSizeChanged());
        latest.setLastLocalCheckMissing(result.getMissing());
        latest.setLastLocalCheckBaselineMissing(result.getBaselineMissing());
        latest.setLastLocalCheckUnverifiable(result.getUnverifiable());
        latest.setLastLocalCheckMessage(result.getMessage());
        fileCategoryRepository.save(latest);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long categoryId, Exception e) {
        FileCategoryEntity latest = resolveLatest(categoryId);
        latest.setLastLocalCheckTime(LocalDateTime.now());
        latest.setLastLocalCheckStatus("FAILED");
        latest.setLastLocalCheckMessage(StringUtils.abbreviate(
                StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), MAX_MESSAGE_LENGTH));
        fileCategoryRepository.save(latest);
    }

    private FileCategoryEntity resolveLatest(Long categoryId) {
        return fileCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryId));
    }
}
