package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.FileGroupRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.CategoryUpsertRequest;
import cc.cc3c.hive.oss.controller.vo.FileCategoryVO;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Component
public class FileCategoryService {
    private final FileCategoryRepository fileCategoryRepository;
    private final FileGroupRepository fileGroupRepository;
    private final HiveRecordRepository hiveRecordRepository;

    public FileCategoryService(FileCategoryRepository fileCategoryRepository,
                               FileGroupRepository fileGroupRepository,
                               HiveRecordRepository hiveRecordRepository) {
        this.fileCategoryRepository = fileCategoryRepository;
        this.fileGroupRepository = fileGroupRepository;
        this.hiveRecordRepository = hiveRecordRepository;
    }

    @Transactional
    public FileCategoryVO createCategory(CategoryUpsertRequest request) {
        if (StringUtils.isBlank(request.getCode())) {
            throw new IllegalArgumentException("code is required");
        }
        if (StringUtils.isBlank(request.getName())) {
            throw new IllegalArgumentException("name is required");
        }
        if (StringUtils.isBlank(request.getBucketName())) {
            throw new IllegalArgumentException("bucketName is required");
        }
        if (StringUtils.isBlank(request.getStorageClass())) {
            throw new IllegalArgumentException("storageClass is required");
        }
        if (StringUtils.isBlank(request.getUiVariant())) {
            throw new IllegalArgumentException("uiVariant is required");
        }
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        if (request.getSortOrder() == null) {
            throw new IllegalArgumentException("sortOrder is required");
        }
        if (fileCategoryRepository.existsByCode(request.getCode().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("category code exists");
        }
        String bucketName = request.getBucketName();
        if (fileCategoryRepository.existsByBucketName(bucketName)) {
            throw new IllegalArgumentException("bucket name exists");
        }
        FileCategoryEntity entity = new FileCategoryEntity();
        entity.setCode(request.getCode().toUpperCase(Locale.ROOT));
        entity.setName(request.getName());
        entity.setBucketName(bucketName);
        entity.setStorageClass(CategoryStorageClass.valueOf(request.getStorageClass().toUpperCase(Locale.ROOT)));
        entity.setUiVariant(request.getUiVariant());
        entity.setEnabled(request.getEnabled());
        entity.setSortOrder(request.getSortOrder());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return toCategoryVO(fileCategoryRepository.save(entity));
    }

    @Transactional
    public FileCategoryVO updateCategory(CategoryUpsertRequest request) {
        if (StringUtils.isBlank(request.getCode())) {
            throw new IllegalArgumentException("code is required");
        }
        FileCategoryEntity entity = fileCategoryRepository.findByCode(request.getCode())
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + request.getCode()));
        if (StringUtils.isBlank(request.getName())) {
            throw new IllegalArgumentException("name is required");
        }
        if (StringUtils.isBlank(request.getUiVariant())) {
            throw new IllegalArgumentException("uiVariant is required");
        }
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        if (request.getSortOrder() == null) {
            throw new IllegalArgumentException("sortOrder is required");
        }
        entity.setName(request.getName());
        entity.setUiVariant(request.getUiVariant());
        entity.setEnabled(request.getEnabled());
        entity.setSortOrder(request.getSortOrder());
        entity.setUpdatedAt(LocalDateTime.now());
        return toCategoryVO(fileCategoryRepository.save(entity));
    }

    public List<FileCategoryVO> listCategories(boolean includeDisabled) {
        List<FileCategoryEntity> entities = includeDisabled
                ? fileCategoryRepository.findAllByOrderBySortOrderAscIdAsc()
                : fileCategoryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();
        return entities.stream().map(this::toCategoryVO).toList();
    }

    @Transactional
    public void deleteCategory(String categoryCode) {
        FileCategoryEntity entity = fileCategoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryCode));
        long groupCount = fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode).size();
        if (groupCount > 0) {
            throw new IllegalStateException("category still has groups");
        }
        if (!hiveRecordRepository.findByCategory_IdAndDeletedIsFalse(entity.getId()).isEmpty()) {
            throw new IllegalStateException("category still has files");
        }
        hiveRecordRepository.detachFromCategory(entity.getId());
        fileCategoryRepository.delete(entity);
    }

    public FileCategoryEntity resolveCategory(String categoryCode) {
        return fileCategoryRepository.findByCodeAndEnabledTrue(normalizeCode(categoryCode))
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryCode));
    }

    private String normalizeCode(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private FileCategoryVO toCategoryVO(FileCategoryEntity category) {
        return FileCategoryVO.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .bucketName(category.getBucketName())
                .storageClass(category.getStorageClass().name())
                .uiVariant(category.getUiVariant())
                .enabled(category.getEnabled())
                .sortOrder(category.getSortOrder())
                .lastSyncTime(category.getLastSyncTime())
                .lastSyncStatus(category.getLastSyncStatus())
                .lastSyncOssTotal(category.getLastSyncOssTotal())
                .lastSyncOssOnly(category.getLastSyncOssOnly())
                .lastSyncOssToDbMatched(category.getLastSyncOssToDbMatched())
                .lastSyncOssToDbMismatched(category.getLastSyncOssToDbMismatched())
                .lastSyncDbToOssMismatched(category.getLastSyncDbToOssMismatched())
                .lastSyncSkipped(category.getLastSyncSkipped())
                .lastSyncMessage(category.getLastSyncMessage())
                .lastLocalCheckTime(category.getLastLocalCheckTime())
                .lastLocalCheckStatus(category.getLastLocalCheckStatus())
                .lastLocalCheckRecordTotal(category.getLastLocalCheckRecordTotal())
                .lastLocalCheckSizeMatched(category.getLastLocalCheckSizeMatched())
                .lastLocalCheckSizeChanged(category.getLastLocalCheckSizeChanged())
                .lastLocalCheckMissing(category.getLastLocalCheckMissing())
                .lastLocalCheckUnverifiable(category.getLastLocalCheckUnverifiable())
                .lastLocalCheckBaselineMissing(category.getLastLocalCheckBaselineMissing())
                .lastLocalCheckMessage(category.getLastLocalCheckMessage())
                .build();
    }
}
