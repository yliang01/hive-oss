package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Data;

import java.util.List;

@Data
public class FileBulkMarkDeleteRequest {
    private List<String> fileKeys;
}
