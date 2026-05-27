package cc.cc3c.hive.oss.web;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.oss.service.FileCategoryResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CategoryContextFactory {
    private final FileCategoryResolver fileCategoryResolver;

    public CategoryContextFactory(FileCategoryResolver fileCategoryResolver) {
        this.fileCategoryResolver = fileCategoryResolver;
    }

    public CategoryContext resolve(String requestedCode) {
        FileCategoryEntity category;
        try {
            category = fileCategoryResolver.resolveCategory(requestedCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        if (StringUtils.isBlank(category.getBucketName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "bucket name not configured for category: " + requestedCode);
        }
        return new CategoryContext(category.getId(), category.getCode(), category.getName(),
                category.getBucketName(), category.getStorageClass(), category.getUiVariant());
    }
}
