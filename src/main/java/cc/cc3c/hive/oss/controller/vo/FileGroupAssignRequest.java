package cc.cc3c.hive.oss.controller.vo;

import lombok.Data;

import java.util.List;

@Data
public class FileGroupAssignRequest {
    private List<String> fileKeys;
    private String operator;
}
