package cc.cc3c.hive.oss;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class HiveOssApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder().sources(HiveOssApplication.class).run(args);
    }
}
