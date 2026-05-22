package cc.cc3c.hive.oss.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.tools.Restore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class H2RestoreTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PENDING_DIR = "pending";
    private static final String HISTORY_DIR = "history";
    private static final String REQUEST_FILE = "restore-request.json";

    public static Path dbDir;

    private H2RestoreTool() {
    }

    public static void run() throws Exception {
        createInternalDirs(dbDir);

        Path requestFile = requestFile(dbDir);
        if (!Files.exists(requestFile)) {
            return;
        }

        JsonNode request = MAPPER.readTree(requestFile.toFile());
        String databaseType = required(request, "databaseType");
        if (!"h2".equalsIgnoreCase(databaseType)) {
            throw new IllegalStateException("Unsupported restore databaseType: " + databaseType);
        }

        String databaseName = required(request, "databaseName");
        Path archivePath = Path.of(required(request, "archivePath")).normalize();
        if (!Files.exists(archivePath)) {
            throw new IllegalStateException("H2 restore archive not found: " + archivePath);
        }

        String stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path currentDb = dbDir.resolve(databaseName + ".mv.db");
        if (Files.exists(currentDb)) {
            Files.copy(currentDb, historyDir(dbDir).resolve(databaseName + ".mv.db.before-restore-" + stamp), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(currentDb);
        }

        Restore.execute(archivePath.toString(), dbDir.toString(), databaseName);
        Files.delete(requestFile);
        Files.delete(archivePath);
    }

    public static void writePendingRequest(String batchId, Path archivePath, String databaseName) throws Exception {
        Path pendingDir = pendingDir(dbDir);
        Path pendingArchive = pendingDir.resolve(archivePath.getFileName()).normalize();
        Files.move(archivePath, pendingArchive, StandardCopyOption.REPLACE_EXISTING);

        var request = MAPPER.createObjectNode();
        request.put("batchId", batchId);
        request.put("databaseType", "h2");
        request.put("databaseName", databaseName);
        request.put("archivePath", pendingArchive.toString().replace('\\', '/'));
        request.put("requestedAt", OffsetDateTime.now().toString());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(requestFile(dbDir).toFile(), request);
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        String text = value == null ? null : value.asText(null);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("restore-request.json missing required field: " + field);
        }
        return text;
    }

    private static void createInternalDirs(Path dbDir) throws Exception {
        Files.createDirectories(dbDir);
        Files.createDirectories(pendingDir(dbDir));
        Files.createDirectories(historyDir(dbDir));
    }

    private static Path pendingDir(Path dbDir) {
        return dbDir.resolve(PENDING_DIR).normalize();
    }

    private static Path historyDir(Path dbDir) {
        return dbDir.resolve(HISTORY_DIR).normalize();
    }

    private static Path requestFile(Path dbDir) {
        return pendingDir(dbDir).resolve(REQUEST_FILE).normalize();
    }
}
