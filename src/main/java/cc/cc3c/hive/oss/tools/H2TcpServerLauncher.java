package cc.cc3c.hive.oss.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.h2.tools.Server;
import org.springframework.core.env.Environment;

public final class H2TcpServerLauncher {

    private static final String ENABLED = "hive.h2.server.enabled";
    private static final String PORT = "hive.h2.server.port";
    private static final String BASE_DIR = "hive.h2.server.baseDir";
    private static final String ALLOW_OTHERS = "hive.h2.server.allowOthers";

    private static volatile Server server;

    private H2TcpServerLauncher() {
    }

    public static synchronized Server startIfEnabled(Environment environment) {
        if (server != null && server.isRunning(false)) {
            return server;
        }
        if (!environment.getRequiredProperty(ENABLED, Boolean.class)) {
            return null;
        }
        String port = environment.getRequiredProperty(PORT);
        String baseDir = environment.getRequiredProperty(BASE_DIR);
        boolean allowOthers = environment.getRequiredProperty(ALLOW_OTHERS, Boolean.class);

        List<String> serverArgs = new ArrayList<>();
        serverArgs.add("-tcp");
        serverArgs.add("-tcpPort");
        serverArgs.add(port);
        serverArgs.add("-baseDir");
        serverArgs.add(baseDir);
        serverArgs.add("-ifNotExists");
        if (allowOthers) {
            serverArgs.add("-tcpAllowOthers");
        }

        try {
            Files.createDirectories(Path.of(baseDir));
            server = Server.createTcpServer(serverArgs.toArray(String[]::new)).start();
            Runtime.getRuntime().addShutdownHook(new Thread(H2TcpServerLauncher::stop, "h2-tcp-server-stop"));
            System.out.println("H2 TCP Server started: " + server.getURL() + ", baseDir=" + baseDir);
            return server;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start H2 TCP Server", e);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
