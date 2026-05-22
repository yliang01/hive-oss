package cc.cc3c.hive.oss.tools;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

public class H2RestoreEnvironmentPreparedListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        Path dbDir = Path.of(env.getRequiredProperty("hive.h2.server.baseDir"));
        try {
            H2RestoreTool.dbDir = dbDir;
            H2RestoreTool.run();
        } catch (Exception e) {
            throw new IllegalStateException("H2 restore failed", e);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
