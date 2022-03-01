package cc.cc3c.hive.oss.client.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HiveOssPartUploadResult {
    private int part;
    private String eTag;
}
