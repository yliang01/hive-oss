package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class FileCategoryResolver {
    private final FileCategoryRepository fileCategoryRepository;

    public FileCategoryResolver(FileCategoryRepository fileCategoryRepository) {
        this.fileCategoryRepository = fileCategoryRepository;
    }

    public String resolveBucket(String categoryCode) {
        String normalizedCode = normalizeCode(categoryCode);
        FileCategoryEntity category = fileCategoryRepository.findByCodeAndEnabledTrue(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryCode));
        if (StringUtils.isBlank(category.getBucketName())) {
            throw new IllegalArgumentException("bucket name not configured for category: " + categoryCode);
        }
        return category.getBucketName();
    }

    public FileCategoryEntity resolveCategory(String categoryCode) {
        return fileCategoryRepository.findByCodeAndEnabledTrue(normalizeCode(categoryCode))
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryCode));
    }

    public FileCategoryEntity resolveFallbackCategory(HiveRecord record) {
        if (StringUtils.isNotBlank(record.getBucketName())) {
            FileCategoryEntity category = fileCategoryRepository.findByBucketNameAndEnabledTrue(record.getBucketName()).orElse(null);
            if (category != null) {
                return category;
            }
        }

        return fileCategoryRepository.findFirstByStorageClassAndEnabledTrueOrderBySortOrderAscIdAsc(CategoryStorageClass.STANDARD)
                .orElse(null);
    }

    public CategoryStorageClass resolveStorageClass(String categoryCode) {
        FileCategoryEntity category = resolveCategory(categoryCode);
        return normalizeStorageClass(category.getStorageClass());
    }

    public CategoryStorageClass resolveStorageClassByBucket(String bucketName) {
        if (StringUtils.isBlank(bucketName)) {
            return CategoryStorageClass.STANDARD;
        }
        return fileCategoryRepository.findByBucketNameAndEnabledTrue(bucketName)
                .map(FileCategoryEntity::getStorageClass)
                .map(this::normalizeStorageClass)
                .orElse(CategoryStorageClass.STANDARD);
    }

    public CategoryStorageClass resolveRecordStorageClass(HiveRecord record) {
        if (record == null) {
            return CategoryStorageClass.STANDARD;
        }
        return resolveStorageClassByBucket(record.getBucketName());
    }

    public FileCategoryEntity resolveDefaultCategoryByStorageClass(CategoryStorageClass storageClass) {
        CategoryStorageClass normalized = storageClass == null ? CategoryStorageClass.STANDARD : storageClass;
        FileCategoryEntity category = fileCategoryRepository.findFirstByStorageClassAndEnabledTrueOrderBySortOrderAscIdAsc(normalized)
                .orElse(null);
        if (category != null) {
            return category;
        }
        if (normalized == CategoryStorageClass.ARCHIVE) {
            return fileCategoryRepository.findFirstByStorageClassAndEnabledTrueOrderBySortOrderAscIdAsc(CategoryStorageClass.STANDARD)
                    .orElseThrow(() -> new IllegalArgumentException("no enabled category for storage class"));
        }
        throw new IllegalArgumentException("no enabled category for storage class");
    }

    private String normalizeCode(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CategoryStorageClass normalizeStorageClass(CategoryStorageClass storageClass) {
        return storageClass == null ? CategoryStorageClass.STANDARD : storageClass;
    }
}
