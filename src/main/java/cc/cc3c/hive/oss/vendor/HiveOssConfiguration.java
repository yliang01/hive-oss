package cc.cc3c.hive.oss.vendor;

import cc.cc3c.hive.oss.vendor.client.HiveOssClient;
import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.client.tencent.TencentOssConfig;
import cc.cc3c.hive.oss.vendor.encryption.HiveOssEncryptionConfig;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HiveOssConfiguration implements InitializingBean {

    @Autowired
    private HiveOssEncryptionConfig encryptionConfig;

    @Autowired
    private AlibabaOssConfig alibabaOssConfig;

    @Autowired
    private TencentOssConfig tencentOssConfig;

    @Autowired
    private HiveOssClient alibabaOssClient;

    @Autowired
    private HiveOssClient tencentOssClient;

    @Bean
    public HiveOss alibabaOss() {
        return new HiveOssImpl(alibabaOssClient);
    }

    @Bean
    public HiveOss tencentOss() {
        return new HiveOssImpl(tencentOssClient);
    }

    @Override
    public void afterPropertiesSet() {
        HiveOssTask.encryptionConfig = encryptionConfig;
        HiveOssTask.alibabaOssConfig = alibabaOssConfig;
        HiveOssTask.tencentOssConfig = tencentOssConfig;
    }
}
