package cc.cc3c.hive.oss;

import cc.cc3c.hive.oss.db.h2.H2RestoreEnvironmentPreparedListener;
import cc.cc3c.hive.oss.db.h2.H2TcpServerEnvironmentPreparedListener;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class HiveOssApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
                .sources(HiveOssApplication.class)
                .listeners(
                        new H2RestoreEnvironmentPreparedListener(),
                        new H2TcpServerEnvironmentPreparedListener()
                )
                .run(args);
    }
}
