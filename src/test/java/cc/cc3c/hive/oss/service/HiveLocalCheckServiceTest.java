package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveLocalCheckStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import org.apache.commons.io.FileUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HiveLocalCheckServiceTest {

    @Test
    void checksOnlyTheProvidedRecordsAgainstTheirCurrentGroupPath() throws Exception {
        Path root = Files.createTempDirectory("hive-local-check-test");
        try {
            HiveLocalCheckService service = service(root);
            Path localFile = root.resolve("DOCS").resolve("GROUP_A").resolve("example.txt");
            Files.createDirectories(localFile.getParent());
            Files.writeString(localFile, "changed");
            HiveRecord record = record("GROUP_A", 1L);

            HiveLocalCheckService.LocalCheckResult result = service.checkRecords("DOCS", List.of(record)).get(record.getId());

            assertThat(result.status()).isEqualTo(HiveLocalCheckStatus.SIZE_CHANGED);
            assertThat(result.observedSize()).isEqualTo(Files.size(localFile));
            assertThat(result.localFile()).isEqualTo(localFile);
        } finally {
            FileUtils.deleteDirectory(root.toFile());
        }
    }

    @Test
    void marksRecordsUnverifiableWhenTheConfiguredRootIsUnavailable() {
        HiveLocalCheckService service = service(Path.of("missing-local-check-root"));
        HiveRecord record = record(null, 1L);

        HiveLocalCheckService.LocalCheckResult result = service.checkRecords("DOCS", List.of(record)).get(record.getId());

        assertThat(result.status()).isEqualTo(HiveLocalCheckStatus.UNVERIFIABLE);
        assertThat(result.observedSize()).isNull();
    }

    private HiveLocalCheckService service(Path root) {
        HiveLocalCheckService service = new HiveLocalCheckService();
        ReflectionTestUtils.setField(service, "localScanDir", root.toString());
        return service;
    }

    private HiveRecord record(String groupCode, long originalSize) {
        HiveRecord record = new HiveRecord();
        record.setId(1);
        record.setFileName("example.txt");
        record.setStatus(HiveRecordStatus.UPLOADED);
        record.setOriginalSize(originalSize);
        if (groupCode != null) {
            FileGroup group = new FileGroup();
            group.setGroupCode(groupCode);
            record.assignTo(group);
        }
        return record;
    }
}
