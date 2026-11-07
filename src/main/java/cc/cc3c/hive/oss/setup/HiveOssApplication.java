package cc.cc3c.hive.oss.setup;

import cc.cc3c.hive.release.common.SecretException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "cc.cc3c.hive.domain.HiveDomainAutoConfiguration",
        "cc.cc3c.hive.encryption.HiveEncryptionAutoConfiguration",
        "cc.cc3c.hive.db2oss.autoconfigure.BackupAutoConfiguration",
        "cc.cc3c.hive.release.publisher.ReleaseAutoConfiguration"})
public class HiveOssApplication {

    public static void main(String[] args) {
        try {
            new SpringApplicationBuilder()
                    .sources(HiveOssApplication.class)
                    .run(args);
        } catch (SecretException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            if (cause instanceof cc.cc3c.hive.release.publisher.ReleaseException)
                System.err.println("ERROR: " + cause.getMessage());
            else System.err.println("ERROR: Hive operation failed; sensitive exception details suppressed.");
            System.exit(1);
        }
    }
}
