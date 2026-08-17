package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.thumbnail.ThumbnailGeneratorService;
import cc.cc3c.hive.oss.web.CategoryContext;
import cc.cc3c.hive.oss.web.CategoryFileContext;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class HiveDownloadServiceTest {

    @Test
    void releasesOnlyAnAvailableRegularDownloadFile() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveRecordRepository recordRepository = mock(HiveRecordRepository.class);
            HiveDownloadService service = service(recordRepository, downloadDir);
            CategoryFileContext context = context();
            Path target = service.getDownloadFile(context).toPath();

            assertThat(service.isDownloadFileAvailable(context)).isFalse();
            assertThat(target).isEqualTo(downloadDir.resolve("DOCS").resolve("GROUP_A").resolve("example.txt"));
            Files.createDirectories(target.getParent());
            Files.writeString(target, "downloaded");

            assertThat(service.isDownloadFileAvailable(context)).isTrue();
            service.releaseLocalStatus(context);

            assertThat(Files.exists(target)).isFalse();
            service.releaseLocalStatus(context);
            verify(recordRepository, times(2)).updateDownloadStatus(1L, "file-key", null);
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    @Test
    void rejectsNonRegularDownloadTargetsWithoutClearingStatus() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveRecordRepository recordRepository = mock(HiveRecordRepository.class);
            HiveDownloadService service = service(recordRepository, downloadDir);
            CategoryFileContext context = context();
            Path target = service.getDownloadFile(context).toPath();
            Files.createDirectories(target.getParent());
            Files.createDirectory(target);

            assertThat(service.isDownloadFileAvailable(context)).isFalse();
            assertThatThrownBy(() -> service.releaseLocalStatus(context))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("not a regular file");

            verifyNoInteractions(recordRepository);
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    @Test
    void usesTheUngroupedDirectoryForRecordsWithoutAGroup() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveDownloadService service = service(mock(HiveRecordRepository.class), downloadDir);
            CategoryFileContext context = context(null);

            assertThat(service.getDownloadFile(context).toPath())
                    .isEqualTo(downloadDir.resolve("DOCS").resolve("_ungrouped").resolve("example.txt"));
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    private HiveDownloadService service(HiveRecordRepository recordRepository, Path downloadDir) {
        HiveDownloadService service = new HiveDownloadService(
                recordRepository,
                mock(HiveRecordImageMetaRepository.class),
                mock(HiveOssService.class),
                mock(ThumbnailGeneratorService.class)
        );
        service.setDownloadDir(downloadDir.toString());
        service.init();
        return service;
    }

    private CategoryFileContext context() {
        return context("GROUP_A");
    }

    private CategoryFileContext context(String groupCode) {
        CategoryContext category = new CategoryContext(1L, "DOCS", "Docs", "bucket", CategoryStorageClass.STANDARD, "file");
        return new CategoryFileContext(category, "file-key", 1, "example.txt", groupCode, false,
                null, null, HiveRecordStatus.UPLOADED, null, null, null);
    }
}
