package cc.cc3c.hive.oss.service;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DbBackupChecksumVerifierTest {

    private final DbBackupChecksumVerifier verifier = new DbBackupChecksumVerifier();

    @Test
    public void verifySha256_passesWhenHashMatches() throws Exception {
        Path tempDir = Files.createTempDirectory("checksum-ok-");
        Path archive = tempDir.resolve("hive.sql.gz");
        Files.writeString(archive, "test-content");
        String expected = sha256Hex(archive);
        Path checksum = tempDir.resolve("hive.sql.gz.sha256");
        Files.writeString(checksum, expected + "  hive.sql.gz" + System.lineSeparator());

        try {
            assertThatCode(() -> verifier.verifySha256(archive, checksum)).doesNotThrowAnyException();
        } finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    public void verifySha256_throwsWhenHashMismatch() throws Exception {
        Path tempDir = Files.createTempDirectory("checksum-fail-");
        Path archive = tempDir.resolve("hive.sql.gz");
        Files.writeString(archive, "test-content");
        Path checksum = tempDir.resolve("hive.sql.gz.sha256");
        Files.writeString(checksum, "deadbeef  hive.sql.gz" + System.lineSeparator());

        try {
            assertThatThrownBy(() -> verifier.verifySha256(archive, checksum))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Checksum mismatch");
        } finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    private static String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        return HexFormat.of().formatHex(hash);
    }
}
