package cc.cc3c.hive.oss.web;

import cc.cc3c.hive.domain.model.CategoryStorageClass;

public record CategoryContext(
        String requestedCode,
        String code,
        Long categoryId,
        String bucketName,
        CategoryStorageClass storageClass,
        String uiVariant
) {
}
