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
        when(downloadService.isDownloadFileAvailable(context)).thenReturn(true);

        ResponseEntity<Void> response = controller.downloadTask(context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(downloadService).reconcileStaleDownloading(context);
        verify(downloadService, never()).download(context);
    }

    @Test
    void rejectsReleaseWhileTheDownloadIsRunning() {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        HiveFileDownloadController controller = new HiveFileDownloadController(downloadService);

        ResponseEntity<Void> response = controller.releaseLocal(context(HiveDownloadStatus.downloading));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(downloadService);
    }

    private CategoryFileContext context(HiveDownloadStatus downloadStatus) {
        CategoryContext category = new CategoryContext(1L, "DOCS", "Docs", "bucket", CategoryStorageClass.STANDARD, "file");
        return new CategoryFileContext(category, "file-key", 1, "example.txt", "GROUP_A", false,
                null, null, HiveRecordStatus.UPLOADED, null, null, downloadStatus);
    }
}
