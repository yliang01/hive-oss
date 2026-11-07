package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.business.web.CategoryContext;
import cc.cc3c.hive.oss.business.web.CategoryFileContext;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
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

            verify(recordRepository, org.mockito.Mockito.never()).updateDownloadStatus(1L, "file-key", null);
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

    @Test
    void allowsOnlyUploadedFilesWithoutAnExistingLocalCopy() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveDownloadService service = service(mock(HiveRecordRepository.class), downloadDir);
            CategoryFileContext uploaded = context(HiveRecordStatus.UPLOADED, CategoryStorageClass.STANDARD, null);

            assertThat(service.isDownloadable(uploaded, null)).isTrue();
            assertThat(service.isDownloadable(context(HiveRecordStatus.OSS_ONLY, CategoryStorageClass.STANDARD, null), null)).isFalse();
            assertThat(service.isDownloadable(context(HiveRecordStatus.DB_ONLY, CategoryStorageClass.STANDARD, null), null)).isFalse();
            assertThat(service.isDownloadable(context(HiveRecordStatus.TO_BE_DELETED, CategoryStorageClass.STANDARD, null), null)).isFalse();
            assertThat(service.isDownloadable(uploaded, HiveDownloadStatus.downloading)).isFalse();

            Files.createDirectories(service.getDownloadFile(uploaded).toPath().getParent());
            Files.writeString(service.getDownloadFile(uploaded).toPath(), "downloaded");
            assertThat(service.isDownloadable(uploaded, null)).isFalse();
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    @Test
    void requiresAnArchiveFileToBeRestoredBeforeDownloading() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveDownloadService service = service(mock(HiveRecordRepository.class), downloadDir);
            CategoryFileContext frozen = context(HiveRecordStatus.UPLOADED, CategoryStorageClass.ARCHIVE, null);

            assertThat(service.isRestoreRequired(frozen)).isTrue();
            assertThat(service.isDownloadable(frozen, null)).isFalse();
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    @Test
    void clearsAStaleDownloadingStatus() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveRecordRepository recordRepository = mock(HiveRecordRepository.class);
            HiveDownloadService service = service(recordRepository, downloadDir);
            CategoryFileContext context = context(HiveRecordStatus.UPLOADED, CategoryStorageClass.STANDARD,
                    HiveDownloadStatus.downloading);

            assertThat(service.reconcileStaleDownloading(context)).isEqualTo(HiveDownloadStatus.failed);
            verify(recordRepository).updateDownloadStatus(1L, "file-key", HiveDownloadStatus.failed);
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    @Test
    void streamsPreviewFromTheManagedLocalDownloadFile() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveDownloadService service = service(mock(HiveRecordRepository.class), downloadDir);
            CategoryFileContext context = context();
            Path target = service.getDownloadFile(context).toPath();
            Files.createDirectories(target.getParent());
            Files.writeString(target, "local preview");
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            service.streamLocalPreview(context, output);

            assertThat(output.toString()).isEqualTo("local preview");
            assertThat(service.getAvailableDownloadFileSize(context)).isEqualTo(13L);
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    @Test
    void movesAnExistingLocalDownloadToTheTargetGroupDirectory() throws Exception {
        Path downloadDir = Files.createTempDirectory("hive-download-test");
        try {
            HiveDownloadService service = service(mock(HiveRecordRepository.class), downloadDir);
            CategoryFileContext sourceContext = context("SOURCE");
            Path source = service.getDownloadFile(sourceContext).toPath();
            Files.createDirectories(source.getParent());
            Files.writeString(source, "downloaded");
            HiveRecord record = new HiveRecord();
            record.setFileName("example.txt");
            record.setZipped(false);
            FileGroup sourceGroup = new FileGroup();
            sourceGroup.setGroupCode("SOURCE");
            record.assignTo(sourceGroup);

            HiveDownloadService.LocalDownloadMove move = service.moveLocalDownload("DOCS", record, "TARGET");

            assertThat(move.source()).isEqualTo(source);
            assertThat(move.target()).isEqualTo(downloadDir.resolve("DOCS").resolve("TARGET").resolve("example.txt"));
            assertThat(Files.exists(source)).isFalse();
            assertThat(Files.readString(move.target())).isEqualTo("downloaded");
        } finally {
            FileUtils.deleteDirectory(downloadDir.toFile());
        }
    }

    private HiveDownloadService service(HiveRecordRepository recordRepository, Path downloadDir) {
        HiveDownloadService service = new HiveDownloadService(
                recordRepository,
                mock(HiveRecordImageMetaRepository.class),
                mock(HiveOssService.class)
        );
        service.setDownloadDir(downloadDir.toString());
        service.init();
        return service;
    }

    private CategoryFileContext context() {
        return context("GROUP_A");
    }

    private CategoryFileContext context(String groupCode) {
        return context(HiveRecordStatus.UPLOADED, CategoryStorageClass.STANDARD, null, groupCode);
    }

    private CategoryFileContext context(HiveRecordStatus recordStatus, CategoryStorageClass storageClass,
                                        HiveDownloadStatus downloadStatus) {
        return context(recordStatus, storageClass, downloadStatus, "GROUP_A");
    }

    private CategoryFileContext context(HiveRecordStatus recordStatus, CategoryStorageClass storageClass,
                                        HiveDownloadStatus downloadStatus, String groupCode) {
        CategoryContext category = new CategoryContext(1L, "DOCS", "Docs", "bucket", storageClass, "file");
        return new CategoryFileContext(category, "file-key", 1, "example.txt", groupCode, false,
                null, null, recordStatus, null, null, downloadStatus);
    }
}
