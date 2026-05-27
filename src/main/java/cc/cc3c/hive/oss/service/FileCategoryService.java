package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
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

    public FileCategoryService(FileCategoryRepository fileCategoryRepository) {
        this.fileCategoryRepository = fileCategoryRepository;
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
        if (fileCategoryRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new IllegalArgumentException("category code exists");
        }
        String bucketName = request.getBucketName().trim();
        if (fileCategoryRepository.existsByBucketNameIgnoreCase(bucketName)) {
            throw new IllegalArgumentException("bucket name exists");
        }
        FileCategoryEntity entity = new FileCategoryEntity();
        entity.setCode(request.getCode().trim().toUpperCase(Locale.ROOT));
        entity.setName(request.getName().trim());
        entity.setBucketName(bucketName);
        entity.setStorageClass(CategoryStorageClass.valueOf(request.getStorageClass().trim().toUpperCase(Locale.ROOT)));
        entity.setUiVariant(request.getUiVariant().trim());
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
        FileCategoryEntity entity = resolveCategory(request.getCode());
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
        entity.setName(request.getName().trim());
        entity.setUiVariant(request.getUiVariant().trim());
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

    public FileCategoryEntity resolveCategory(String categoryCode) {
        return fileCategoryRepository.findByCodeAndEnabledTrue(normalizeCode(categoryCode))
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryCode));
    }

    private String normalizeCode(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }
        return code.trim().toUpperCase(Locale.ROOT);
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
                .lastSyncMessage(category.getLastSyncMessage())
                .build();
    }
}
