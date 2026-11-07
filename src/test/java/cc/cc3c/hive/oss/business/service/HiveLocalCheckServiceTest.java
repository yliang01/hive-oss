package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveLocalCheckStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.business.web.CategoryContext;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HiveLocalCheckServiceTest {

    @Test
    void comparesTheManagedDownloadFileWithTheOriginalSize() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-local-check-test");
        try {
            HiveDownloadService downloadService = new HiveDownloadService(
                    mock(HiveRecordRepository.class), mock(HiveRecordImageMetaRepository.class), mock(HiveOssService.class));
            downloadService.setDownloadDir(downloadDir.toString());
            downloadService.init();
            HiveLocalCheckService service = new HiveLocalCheckService(downloadService);
            CategoryContext category = new CategoryContext(1L, "DOCS", "Docs", "bucket", CategoryStorageClass.STANDARD, "file");
            HiveRecord record = record(4L);
            Path localFile = downloadService.getDownloadFile(
                    new cc.cc3c.hive.oss.business.web.CategoryFileContext(category, "file-key", 1, "example.txt", "GROUP_A", false,
                            null, null, HiveRecordStatus.UPLOADED, null, null, null)).toPath();
            Files.createDirectories(localFile.getParent());
            Files.writeString(localFile, "changed");

            HiveLocalCheckService.LocalCheckResult result = service.checkRecord(category, record);

            assertThat(result.status()).isEqualTo(HiveLocalCheckStatus.SIZE_CHANGED);
            assertThat(result.observedSize()).isEqualTo(7L);
            assertThat(result.localFile()).isEqualTo(localFile);
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    private HiveRecord record(long originalSize) {
        FileGroup group = new FileGroup();
        group.setGroupCode("GROUP_A");
        HiveRecord record = new HiveRecord();
        record.setId(1);
        record.setFileKey("file-key");
        record.setFileName("example.txt");
        record.setZipped(false);
        record.setStatus(HiveRecordStatus.UPLOADED);
        record.setOriginalSize(originalSize);
        record.assignTo(group);
        return record;
    }
}
