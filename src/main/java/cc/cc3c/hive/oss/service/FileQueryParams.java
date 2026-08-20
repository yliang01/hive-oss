package cc.cc3c.hive.oss.service;

public record FileQueryParams(
        String groupCode,
        boolean ungroupedOnly,
        String keyword,
        int page,
        int pageSize,
        java.util.List<FileSortRule> sortRules
) {
}
