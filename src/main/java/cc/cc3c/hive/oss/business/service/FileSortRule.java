package cc.cc3c.hive.oss.business.service;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public record FileSortRule(FileSortField field, Sort.Direction direction) {
    private static final int MAX_RULES = 3;

    public static List<FileSortRule> parse(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_RULES) {
            throw new IllegalArgumentException("At most " + MAX_RULES + " file sort rules are allowed");
        }

        EnumSet<FileSortField> fields = EnumSet.noneOf(FileSortField.class);
        List<FileSortRule> rules = new ArrayList<>(values.size());
        for (String value : values) {
            String[] parts = value == null ? new String[0] : value.split(":", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("Invalid file sort rule: " + value);
            }
            FileSortField field = FileSortField.fromParameter(parts[0]);
            if (!fields.add(field)) {
                throw new IllegalArgumentException("Duplicate file sort field: " + parts[0]);
            }
            try {
                rules.add(new FileSortRule(field, Sort.Direction.fromString(parts[1])));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid file sort direction: " + parts[1]);
            }
        }
        return List.copyOf(rules);
    }
}
