package cc.cc3c.hive.oss.business.web;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;

public final class FileKeyNormalizer {
    private FileKeyNormalizer() {
    }

    public static String normalize(String fileKey) {
        if (fileKey == null) {
            return null;
        }
        return fileKey.startsWith("/") ? fileKey.substring(1) : fileKey;
    }

    public static List<String> normalizeDistinct(Collection<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return List.of();
        }
        return fileKeys.stream()
                .map(FileKeyNormalizer::normalize)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }
}
