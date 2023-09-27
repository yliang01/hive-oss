package cc.cc3c.hive.oss.vendor.encryption;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "hive.oss.encryption")
public class HiveOssEncryptionConfig {
    private String keyAlgorithm;
    private String cipherAlgorithm;
    private String salt;
    private String password;
}
