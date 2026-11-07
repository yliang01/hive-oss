package cc.cc3c.hive.oss.business.controller;

import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.oss.business.service.HiveDownloadService;
import cc.cc3c.hive.oss.business.web.CategoryContext;
import cc.cc3c.hive.oss.business.web.CategoryFileContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiveFilePreviewControllerTest {

    @Test
    void streamsTheManagedLocalFileForAnAllowedPreview() throws Exception {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        CategoryFileContext context = context();
        when(downloadService.isDownloadFileAvailable(context)).thenReturn(true);
        when(downloadService.getAvailableDownloadFileSize(context)).thenReturn(5L);
        doAnswer(invocation -> {
            invocation.getArgument(1, java.io.OutputStream.class).write("local".getBytes());
            return null;
        }).when(downloadService).streamLocalPreview(any(), any());
        HiveRecordVOAssembler assembler = new HiveRecordVOAssembler(downloadService);
        assembler.setPreviewMaxSizeBytes(10_000);
        HiveFilePreviewController controller = new HiveFilePreviewController(mock(HiveRecordImageMetaRepository.class), downloadService, assembler);

        ResponseEntity<?> response = controller.previewFile(context);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ((StreamingResponseBody) response.getBody()).writeTo(output);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(5L);
        assertThat(output.toString()).isEqualTo("local");
        verify(downloadService).streamLocalPreview(context, output);
    }

    @Test
    void rejectsPreviewBeforeTheFileIsDownloaded() throws Exception {
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        CategoryFileContext context = context();
        when(downloadService.isDownloadFileAvailable(context)).thenReturn(false);
        HiveRecordVOAssembler assembler = new HiveRecordVOAssembler(downloadService);
        assembler.setPreviewMaxSizeBytes(10_000);
        HiveFilePreviewController controller = new HiveFilePreviewController(mock(HiveRecordImageMetaRepository.class), downloadService, assembler);

        ResponseEntity<?> response = controller.previewFile(context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(downloadService, never()).getAvailableDownloadFileSize(context);
    }

    private CategoryFileContext context() {
        CategoryContext category = new CategoryContext(1L, "DOCS", "Docs", "bucket", CategoryStorageClass.STANDARD, "file");
        return new CategoryFileContext(category, "file-key", 1, "example.txt", "GROUP_A", false,
                null, 5L, HiveRecordStatus.UPLOADED, null, null, null);
    }
}
