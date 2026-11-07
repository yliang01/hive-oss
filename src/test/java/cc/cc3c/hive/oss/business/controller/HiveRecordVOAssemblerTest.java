package cc.cc3c.hive.oss.business.controller;

import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.business.service.HiveDownloadService;
import cc.cc3c.hive.oss.business.web.CategoryContext;
import cc.cc3c.hive.oss.business.web.CategoryFileContext;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HiveRecordVOAssemblerTest {

    @Test
    void requiresTheManagedLocalFileForPreview() {
        HiveRecordVOAssembler assembler = new HiveRecordVOAssembler(mock(HiveDownloadService.class));
        assembler.setPreviewMaxSizeBytes(10_000);

        PreviewDecision decision = assembler.evaluatePreview(context(HiveRecordStatus.UPLOADED, false, CategoryStorageClass.STANDARD), false);

        assertThat(decision.previewable()).isFalse();
        assertThat(decision.blockedReason()).isEqualTo(HiveRecordVOAssembler.PREVIEW_BLOCKED_LOCAL_MISSING);
    }

    @Test
    void blocksArchiveAndZipFilesBeforeLocalPreview() {
        HiveRecordVOAssembler assembler = new HiveRecordVOAssembler(mock(HiveDownloadService.class));
        assembler.setPreviewMaxSizeBytes(10_000);

        PreviewDecision archive = assembler.evaluatePreview(context(HiveRecordStatus.UPLOADED, false, CategoryStorageClass.ARCHIVE), true);
        PreviewDecision zipped = assembler.evaluatePreview(context(HiveRecordStatus.UPLOADED, true, CategoryStorageClass.STANDARD), true);

        assertThat(archive.blockedReason()).isEqualTo(HiveRecordVOAssembler.PREVIEW_BLOCKED_ARCHIVE);
        assertThat(zipped.blockedReason()).isEqualTo(HiveRecordVOAssembler.PREVIEW_BLOCKED_ZIP_ARCHIVE);
    }

    private CategoryFileContext context(HiveRecordStatus status, boolean zipped, CategoryStorageClass storageClass) {
        CategoryContext category = new CategoryContext(1L, "DOCS", "Docs", "bucket", storageClass, "file");
        return new CategoryFileContext(category, "file-key", 1, "example.txt", "GROUP_A", zipped,
                null, 5L, status, null, null, null);
    }
}
