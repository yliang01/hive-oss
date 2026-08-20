package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import cc.cc3c.hive.oss.web.CategoryContext;
import cc.cc3c.hive.oss.web.CategoryFileContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HiveFileDownloadControllerTest {

    @Test
    void rejectsDownloadWhenTheTargetFileExists() throws Exception {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        HiveFileDownloadController controller = new HiveFileDownloadController(downloadService);
        CategoryFileContext context = context(null);
        when(downloadService.download(context)).thenReturn(false);

        ResponseEntity<?> response = controller.downloadTask(context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(downloadService).download(context);
    }

    @Test
    void rejectsFilesThatAreNotUploaded() throws Exception {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        HiveFileDownloadController controller = new HiveFileDownloadController(downloadService);
        CategoryFileContext context = context(HiveRecordStatus.OSS_ONLY, null, CategoryStorageClass.STANDARD);

        ResponseEntity<?> response = controller.downloadTask(context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(downloadService).download(context);
    }

    @Test
    void rejectsFrozenArchiveFiles() throws Exception {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        HiveFileDownloadController controller = new HiveFileDownloadController(downloadService);
        CategoryFileContext context = context(HiveRecordStatus.UPLOADED, null, CategoryStorageClass.ARCHIVE);
        when(downloadService.isRestoreRequired(context)).thenReturn(true);

        ResponseEntity<?> response = controller.downloadTask(context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        verify(downloadService, never()).download(context);
    }

    @Test
    void startsDownloadWhenServiceAdmitsIt() throws Exception {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        HiveFileDownloadController controller = new HiveFileDownloadController(downloadService);
        CategoryFileContext context = context(HiveRecordStatus.UPLOADED, HiveDownloadStatus.downloading, CategoryStorageClass.STANDARD);
        when(downloadService.download(context)).thenReturn(true);

        ResponseEntity<?> response = controller.downloadTask(context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(downloadService).download(context);
    }

    @Test
    void rejectsReleaseWhileTheDownloadIsRunning() {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        HiveFileDownloadController controller = new HiveFileDownloadController(downloadService);

        ResponseEntity<?> response = controller.releaseLocal(context(HiveDownloadStatus.downloading));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(downloadService);
    }

    private CategoryFileContext context(HiveDownloadStatus downloadStatus) {
        return context(HiveRecordStatus.UPLOADED, downloadStatus, CategoryStorageClass.STANDARD);
    }

    private CategoryFileContext context(HiveRecordStatus recordStatus, HiveDownloadStatus downloadStatus,
                                        CategoryStorageClass storageClass) {
        CategoryContext category = new CategoryContext(1L, "DOCS", "Docs", "bucket", storageClass, "file");
        return new CategoryFileContext(category, "file-key", 1, "example.txt", "GROUP_A", false,
                null, null, recordStatus, null, null, downloadStatus);
    }
}
