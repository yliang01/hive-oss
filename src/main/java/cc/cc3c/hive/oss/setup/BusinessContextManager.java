package cc.cc3c.hive.oss.setup;

import cc.cc3c.hive.oss.business.config.SecretConfigurationSnapshot;
import cc.cc3c.hive.oss.business.db.h2.H2RestoreTool;
import cc.cc3c.hive.oss.business.db.h2.H2ServerProperties;
import cc.cc3c.hive.oss.business.db.h2.H2TcpServerLauncher;
import cc.cc3c.hive.oss.runtime.BusinessContextConfiguration;
import cc.cc3c.hive.release.common.SecretException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.nio.file.Path;
import java.util.Arrays;

public class BusinessContextManager {
    private final ConfigurableApplicationContext applicationContext;
    private final BusinessServlet servlet;
    private volatile AnnotationConfigWebApplicationContext business;
    private byte[] yaml;

    public BusinessContextManager(ConfigurableApplicationContext applicationContext, BusinessServlet servlet) {
        this.applicationContext = applicationContext;
        this.servlet = servlet;
    }

    public boolean ready() {
        return business != null;
    }

    public synchronized void start(byte[] configuration) throws Exception {
        if (business != null) throw new SecretException("业务已运行；更新配置请重启程序。");
        var context = new AnnotationConfigWebApplicationContext();
        context.setId("hive-business");
        context.setServletContext(((WebApplicationContext) applicationContext).getServletContext());
        context.setServletConfig(servlet.getServletConfig());
        var environment = new StandardServletEnvironment();
        environment.merge(applicationContext.getEnvironment());
        environment.getPropertySources().remove("configurationProperties");
        for (var source : SecretYaml.load(configuration)) {
            Object profile = source.getProperty("spring.config.activate.on-profile");
            if (profile == null || environment.acceptsProfiles(Profiles.of(profile.toString())))
                environment.getPropertySources().addFirst(source);
        }
        context.setEnvironment(environment);
        byte[] snapshot = configuration.clone();
        // No parent context or shared lifecycle coordinator: expose only an immutable YAML snapshot.
        context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("secretConfigurationSnapshot",
                (SecretConfigurationSnapshot) () -> snapshot.clone()));
        context.register(BusinessContextConfiguration.class);
        try {
            var h2 = Binder.get(environment).bind("hive.h2.server", H2ServerProperties.class).get();
            H2RestoreTool.dbDir = Path.of(h2.getBaseDir());
            H2RestoreTool.run();
            H2TcpServerLauncher.startIfEnabled(h2.getEnabled(), h2.getPort().toString(), h2.getBaseDir(), h2.getAllowOthers());
            context.refresh();
            servlet.activate(context);
            yaml = snapshot;
            business = context;
        } catch (Exception exception) {
            servlet.deactivate();
            context.close();
            H2TcpServerLauncher.stop();
            Arrays.fill(snapshot, (byte) 0);
            throw new SecretException("业务初始化失败，请检查数据库、配置和网络；管理页面仍可使用。");
        }
    }

    public synchronized void close() {
        servlet.deactivate();
        var current = business;
        business = null;
        if (current != null) current.close();
        H2TcpServerLauncher.stop();
        if (yaml != null) Arrays.fill(yaml, (byte) 0);
        yaml = null;
    }
}
