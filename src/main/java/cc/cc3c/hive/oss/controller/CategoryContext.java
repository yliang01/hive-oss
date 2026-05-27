package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.model.CategoryStorageClass;

public record CategoryContext(
        String requestedCode,
        String code,
        Long categoryId,
        FileCategoryEntity category,
        String bucketName,
        CategoryStorageClass storageClass,
        String uiVariant
) {
}
