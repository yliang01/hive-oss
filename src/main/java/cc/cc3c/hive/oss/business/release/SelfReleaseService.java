package cc.cc3c.hive.oss.business.release;

import cc.cc3c.hive.release.common.ReleasePaths;
import cc.cc3c.hive.release.publisher.ReleasePublisher;
import cc.cc3c.hive.release.publisher.PublishResult;
import cc.cc3c.hive.release.publisher.ReleaseException;
import cc.cc3c.hive.release.common.DownloadTicket;
import cc.cc3c.hive.release.common.SecretException;
import cc.cc3c.hive.oss.business.config.SecretConfigurationSnapshot;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
public class SelfReleaseService {
    public record View(String id, String version, String state,
                       String archiveSha256, String configSha256, List<String> messages) {
    }

    private final SecretConfigurationSnapshot configuration;
    private final ReleasePublisher publisher;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<String> messages = new ArrayList<>();
    private String id = "", version = "", state = "idle", zipHash = "", bundleHash = "";

    public SelfReleaseService(SecretConfigurationSnapshot configuration, ReleasePublisher publisher) {
        this.configuration = configuration;
        this.publisher = publisher;
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        executor.shutdown();
    }

    public synchronized View view() {
        return new View(id, version, state, zipHash, bundleHash, List.copyOf(messages));
    }

    private synchronized void message(String line) {
        if (messages.size() < 100) messages.add(line);
    }

    public synchronized View publish(String requestedVersion, char[] password) {
        if ("publishing".equals(state)) throw new SecretException("已有发布任务正在执行。");
        id = UUID.randomUUID().toString();
        version = requestedVersion;
        state = "publishing";
        messages.clear();
        zipHash = "";
        bundleHash = "";
        char[] ownedPassword = password.clone();
        executor.submit(() -> {
            try {
                var directory = ReleasePaths.uploadDirectory();
                clearPreviousFiles(directory);
                Files.createDirectories(directory);
                byte[] yaml = configuration.yaml();
                PublishResult result;
                try {
                    result = publisher.publish(version, ReleasePaths.buildDirectory(), yaml, ownedPassword, directory, this::message);
                } finally { Arrays.fill(yaml, (byte) 0); }
                synchronized (this) {
                    zipHash = result.archiveSha256();
                    bundleHash = result.configurationSha256();
                }
                synchronized (this) {
                    state = "complete";
                    message("发布完成，" + ReleasePaths.MANIFEST + " 已上传。");
                }
            } catch (Exception e) {
                fail(e);
            } finally {
                Arrays.fill(ownedPassword, '\0');
            }
        });
        return view();
    }

    private synchronized void fail(Exception exception) {
        state = "failed";
        message(exception instanceof SecretException || exception instanceof ReleaseException ? exception.getMessage() : "任务失败；敏感错误详情已隐藏。已有云端对象保留，本次本地发布文件保留。");
    }

    private void clearPreviousFiles(Path directory) throws Exception {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    public DownloadTicket ticket(String version, int seconds) throws Exception {
        return publisher.createTicket(version, Duration.ofSeconds(seconds), ignored -> { });
    }

}
