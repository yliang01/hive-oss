package cc.cc3c.hive.oss.business.service;

public record FileQueryParams(
        String groupCode,
        boolean ungroupedOnly,
        String keyword,
        int page,
        int pageSize,
        java.util.List<FileSortRule> sortRules
) {
}
