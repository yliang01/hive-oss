package cc.cc3c.hive.oss.vendor.client.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HiveOssPartUploadResult {
    private int part;
    private String eTag;
}
