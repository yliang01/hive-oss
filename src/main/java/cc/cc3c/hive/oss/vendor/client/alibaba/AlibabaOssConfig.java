package cc.cc3c.hive.oss.vendor.client.alibaba;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;


@Data
@Validated
@Component
@ConfigurationProperties(prefix = "hive.oss.alibaba")
public class AlibabaOssConfig {
    @NotBlank
    private String accessKeyId;
    @NotBlank
    private String accessKeySecret;
    @NotBlank
    private String endPoint;
    @NotBlank
    private String standardBucket;
    @NotBlank
    private String archiveBucket;
}
