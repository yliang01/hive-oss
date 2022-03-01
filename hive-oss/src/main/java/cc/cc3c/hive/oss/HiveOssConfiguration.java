package cc.cc3c.hive.oss;

import cc.cc3c.hive.oss.client.HiveOssClient;
import cc.cc3c.hive.oss.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.client.tencent.TencentOssConfig;
import cc.cc3c.hive.oss.encryption.HiveOssEncryptionConfig;
import cc.cc3c.hive.oss.vo.HiveOssTask;
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
