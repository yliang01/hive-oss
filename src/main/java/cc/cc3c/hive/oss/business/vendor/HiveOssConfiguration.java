package cc.cc3c.hive.oss.business.vendor;

import cc.cc3c.hive.encryption.HiveEncryptionConfig;
import cc.cc3c.hive.oss.business.vendor.client.HiveOssClient;
import cc.cc3c.hive.oss.business.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.business.vendor.client.tencent.TencentOssConfig;
import cc.cc3c.hive.oss.business.vendor.vo.HiveOssTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Configuration
public class HiveOssConfiguration implements InitializingBean {

    @Autowired
    private HiveEncryptionConfig encryptionConfig;

    @Autowired
    private AlibabaOssConfig alibabaOssConfig;

    @Autowired(required = false)
    private TencentOssConfig tencentOssConfig;

    @Autowired
    @Qualifier("alibabaOssClient")
    private HiveOssClient alibabaOssClient;

    @Autowired(required = false)
    @Qualifier("tencentOssClient")
    private HiveOssClient tencentOssClient;

    @Bean
    public HiveOss alibabaOss() {
        return new HiveOssImpl(alibabaOssClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "hive.oss.tencent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HiveOss tencentOss() {
        return new HiveOssImpl(tencentOssClient);
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Alibaba OSS configuration initialized");
        HiveOssTask.encryptionConfig = encryptionConfig;
        HiveOssTask.alibabaOssConfig = alibabaOssConfig;
        HiveOssTask.tencentOssConfig = tencentOssConfig;
    }
}
