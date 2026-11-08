package cc.cc3c.hive.oss.business.release;

import cc.cc3c.hive.release.common.ReleasePaths;
import cc.cc3c.hive.release.publisher.ReleasePublisher;
import cc.cc3c.hive.release.publisher.PublishResult;
import cc.cc3c.hive.release.publisher.PublishedRelease;
import cc.cc3c.hive.release.publisher.ReleaseException;
import cc.cc3c.hive.release.common.ReleaseArtifact;
import cc.cc3c.hive.release.common.ReleaseManifest;
import cc.cc3c.hive.release.common.SecretException;
import cc.cc3c.hive.oss.business.config.SecretConfigurationSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SelfReleaseService {
    public record View(String id, String version, String state,
                       String archiveSha256, String configSha256, List<String> messages) {
    }

    private final SecretConfigurationSnapshot configuration;
    private final ReleasePublisher publisher;
    private final ObjectMapper mapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<String> messages = new ArrayList<>();
    private String id = "", version = "", state = "idle", zipHash = "", bundleHash = "";

    public SelfReleaseService(SecretConfigurationSnapshot configuration, ReleasePublisher publisher, ObjectMapper mapper) {
        this.configuration = configuration;
        this.publisher = publisher;
        this.mapper = mapper;
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

    public ReleaseManifest createManifest(String version, int seconds) throws IOException {
        return publisher.createManifest(version, Duration.ofSeconds(seconds), ignored -> { });
    }

    public byte[] manifestPackage(String version, int seconds) throws IOException {
        ReleaseManifest manifest = createManifest(version, seconds);
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(ReleasePaths.MANIFEST));
            zip.write(mapper.writeValueAsBytes(manifest));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("download-program.html"));
            zip.write(downloadPage(version, manifest).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static String downloadPage(String version, ReleaseManifest manifest) {
        ReleaseArtifact program = manifest.files().stream()
                .filter(file -> ReleasePaths.PROGRAM_ARCHIVE.equals(file.name())).findFirst().orElseThrow();
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>下载 Hive %s</title>
                  <style>
                    body { margin: 0; padding: 40px 20px; background: #f4f7fb; color: #172033; font: 16px/1.6 system-ui, sans-serif; }
                    main { max-width: 560px; margin: 8vh auto; padding: 32px; border: 1px solid #e2e8f0; border-radius: 16px; background: white; box-shadow: 0 12px 32px #17203312; }
                    h1 { margin: 0 0 8px; font-size: 1.5rem; }
                    p { color: #536179; }
                    .button { display: inline-block; margin-top: 12px; padding: 12px 20px; border-radius: 9px; background: #2563eb; color: white; font-weight: 650; text-decoration: none; }
                    .expiry { margin-top: 24px; font-size: .88rem; overflow-wrap: anywhere; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>Hive %s</h1>
                    <p>点击下方按钮下载程序包。此页面可以复制到其他电脑后，在浏览器中打开。</p>
                    <a class="button" href="%s" target="_blank" rel="noopener">下载程序</a>
                    <p class="expiry">下载链接有效期至：%s</p>
                  </main>
                </body>
                </html>
                """.formatted(escapeHtml(version), escapeHtml(version),
                escapeHtml(program.url()), escapeHtml(program.expiresAt()));
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    public List<PublishedRelease> releases() throws IOException {
        return publisher.listPublishedReleases();
    }

    public void deleteReleases(List<String> versions) throws IOException {
        publisher.deleteReleases(versions);
    }

}
