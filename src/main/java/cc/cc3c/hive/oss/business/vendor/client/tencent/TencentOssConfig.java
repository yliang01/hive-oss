package cc.cc3c.hive.oss.business.vendor.client.tencent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;


@Data
@Validated
@Component
@ConditionalOnProperty(prefix = "hive.oss.tencent", name = "enabled", havingValue = "true", matchIfMissing = true)
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
