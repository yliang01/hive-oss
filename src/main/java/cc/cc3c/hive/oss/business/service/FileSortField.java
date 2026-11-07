package cc.cc3c.hive.oss.business.service;

import org.springframework.data.domain.Sort;

public enum FileSortField {
    UPDATE_TIME("updateTime", "updateTime"),
    FILE_NAME("fileName", "fileName"),
    SIZE("size", "size");

    private final String parameter;
    private final String property;

    FileSortField(String parameter, String property) {
        this.parameter = parameter;
        this.property = property;
    }

    public static FileSortField fromParameter(String parameter) {
        for (FileSortField field : values()) {
            if (field.parameter.equals(parameter)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unsupported file sort field: " + parameter);
    }

    public Sort.Order toOrder(Sort.Direction direction) {
        Sort.Order order = new Sort.Order(direction, property);
        return this == FILE_NAME ? order.ignoreCase() : order;
    }
}
