package cc.cc3c.hive.oss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class HiveOssApplication implements InitializingBean {

    public static void main(String[] args) {
        SpringApplication.run(HiveOssApplication.class, args);
    }

    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    @Value("${hive.oss.alibaba.endPoint}")
    private String alibabaEndpoint;
    @Value("${hive.oss.alibaba.standardBucket}")
    private String alibabaStandardBucket;
    @Value("${hive.oss.alibaba.achieveBucket}")
    private String alibabaAchieveBucket;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("datasource url: {}", datasourceUrl);
        log.info("alibaba endPoint: {}", alibabaEndpoint);
        log.info("alibaba standardBucket: {}", alibabaStandardBucket);
        log.info("alibaba achieveBucket: {}", alibabaAchieveBucket);
    }
}
