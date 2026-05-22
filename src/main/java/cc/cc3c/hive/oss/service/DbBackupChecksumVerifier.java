package cc.cc3c.hive.oss.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class DbBackupChecksumVerifier {

    public void verifySha256(Path archiveFile, Path checksumFile) throws IOException {
        String expectedHash = readExpectedHash(checksumFile);
        String actualHash = sha256Hex(archiveFile);
        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            throw new IllegalStateException("Checksum mismatch for archive: " + archiveFile.getFileName());
        }
    }

    private String readExpectedHash(Path checksumFile) throws IOException {
        List<String> lines = Files.readAllLines(checksumFile);
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 0 && !parts[0].isBlank()) {
                    return parts[0];
                }
            }
        }
        throw new IOException("Checksum file is empty: " + checksumFile);
    }

    private String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }
}
