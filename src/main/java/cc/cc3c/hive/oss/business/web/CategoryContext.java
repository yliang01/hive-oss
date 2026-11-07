package cc.cc3c.hive.oss.business.web;

import cc.cc3c.hive.domain.model.CategoryStorageClass;

public record CategoryContext(
        Long categoryId,
        String code,
        String name,
        String bucketName,
        CategoryStorageClass storageClass,
        String uiVariant
) {
}
