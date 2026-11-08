package cc.cc3c.hive.oss.setup;

import cc.cc3c.hive.release.common.ReleasePaths;
import cc.cc3c.hive.release.consumer.ReleaseDownloader;
import cc.cc3c.hive.release.common.SecretBundle;
import cc.cc3c.hive.release.common.SecretException;
import cc.cc3c.hive.release.common.ReleaseManifest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

@Service
public class SetupService {
    private static final ObjectMapper MANIFEST_JSON = new ObjectMapper().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    public record Status(boolean ready, String state, String candidate, String message, String runningBuild) {
    }

    private final BusinessContextManager contextManager;
    private final String runningBuild;
    private volatile Path candidate;
    private volatile String state = "locked", message = "请导入发布 manifest 后解锁，或加载秘密 YAML。";

    public SetupService(BusinessContextManager contextManager,
                        ObjectProvider<BuildProperties> buildProperties) throws Exception {
        this.contextManager = contextManager;
        var build = buildProperties.getIfAvailable();
        runningBuild = build == null ? "IDE（无构建信息）" : build.getVersion() + " / " + build.getTime();
        Path selection = ReleasePaths.downloadDirectory().resolve(ReleasePaths.CURRENT_SELECTION);
        if (Files.isRegularFile(selection)) candidate = Path.of(Files.readString(selection).trim());
        else if (Files.isRegularFile(Path.of("config").resolve(ReleasePaths.SECRET_BUNDLE)))
            candidate = Path.of("config").resolve(ReleasePaths.SECRET_BUNDLE);
    }

    public Status status() {
        return new Status(contextManager.ready(), state, candidate == null ? "" : candidate.toString(), message,
                runningBuild);
    }

    public synchronized Status downloadManifest(String manifestJson) throws Exception {
        state = "downloading";
        try {
            ReleaseManifest parsed;
            try {
                parsed = MANIFEST_JSON.readValue(manifestJson, ReleaseManifest.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new SecretException("Invalid release metadata; details suppressed.");
            }
            candidate = new ReleaseDownloader().downloadConfiguration(parsed).configuration();
            state = "locked";
            message = "配置包已下载并校验，请输入解锁密码。";
            return status();
        } catch (Exception e) {
            state = "failed";
            message = "下载失败，已有配置保留。";
            throw e;
        }
    }

    public synchronized Status unlock(char[] password) throws Exception {
        state = "starting";
        try {
            if (candidate == null) throw new SecretException("请先下载配置包或加密秘密 YAML。");
            byte[] yaml = SecretBundle.decrypt(candidate, password);
            Files.createDirectories(ReleasePaths.downloadDirectory());
            try {
                contextManager.start(yaml);
            } finally {
                Arrays.fill(yaml, (byte) 0);
            }
            Files.writeString(ReleasePaths.downloadDirectory().resolve(ReleasePaths.CURRENT_SELECTION), candidate.toAbsolutePath().toString());
            state = "ready";
            message = "业务已启动。";
            return status();
        } catch (Exception e) {
            state = "failed";
            message = "解锁或业务启动失败，已有配置保留。";
            throw e;
        }
    }

    public synchronized Status loadYaml(byte[] yaml) throws Exception {
        try {
            contextManager.start(yaml);
            state = "ready";
            message = "Spring 已读取导入的秘密 YAML 并启动业务。";
            return status();
        } finally {
            Arrays.fill(yaml, (byte) 0);
        }
    }

}
