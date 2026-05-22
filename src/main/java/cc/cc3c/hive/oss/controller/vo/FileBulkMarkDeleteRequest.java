package cc.cc3c.hive.oss.controller.vo;

import lombok.Data;

import java.util.List;

@Data
public class FileBulkMarkDeleteRequest {
    private List<String> fileKeys;
}
