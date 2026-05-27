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
}
