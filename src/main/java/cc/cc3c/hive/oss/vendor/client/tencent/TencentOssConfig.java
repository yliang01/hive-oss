package cc.cc3c.hive.oss.vendor.client.tencent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;


@Data
@Validated
@Component
@ConfigurationProperties(prefix = "hive.oss.tencent")
public class TencentOssConfig {
    @NotBlank
    private String secretId;
    @NotBlank
    private String secretKey;
    @NotBlank
    private String region;
    @NotBlank
    private String bucket;
}
