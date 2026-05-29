package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import org.testng.annotations.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HiveRecordStateServiceTest {

    private final HiveRecordStateService stateService = new HiveRecordStateService();

    @Test
    void uploadLifecycleAllowsNewUploadSuccessAndDelete() {
        HiveRecord record = new HiveRecord();

        stateService.startUpload(record, category(), "a.txt", "key-1");
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.UPLOADING);
        assertThat(record.getDeletable()).isFalse();

        stateService.markUploaded(record, CategoryStorageClass.STANDARD);
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.UPLOADED);
        assertThat(record.getDeletable()).isTrue();

        stateService.markDelete(record);
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.TO_BE_DELETED);
        assertThat(record.getDeletable()).isFalse();

        stateService.confirmDeleted(record);
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.TO_BE_DELETED);
        assertThat(record.getDeleted()).isTrue();
        assertThat(record.getCategory()).isNull();
    }

    @Test
    void startUploadRejectsPendingDeleteAndDbOnly() {
        HiveRecord pendingDelete = record(HiveRecordStatus.TO_BE_DELETED);
        assertThatThrownBy(() -> stateService.startUpload(pendingDelete, category(), "a.txt", "key-1"))
                .isInstanceOf(HiveRecordStateTransitionException.class)
                .hasMessageContaining("START_UPLOAD");

        HiveRecord dbOnly = record(HiveRecordStatus.DB_ONLY);
        assertThatThrownBy(() -> stateService.startUpload(dbOnly, category(), "a.txt", "key-1"))
                .isInstanceOf(HiveRecordStateTransitionException.class)
                .hasMessageContaining("START_UPLOAD");
    }

    @Test
    void uploadSuccessCanRefreshRemoteMetadata() {
        HiveRecord record = new HiveRecord();
        FileCategoryEntity category = category();
        HiveOssObject object = object("key-1");
        object.setSize(512L);

        stateService.startUpload(record, category, "a.txt", "key-1");
        stateService.markUploaded(record, category, object, CategoryStorageClass.STANDARD);

        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.UPLOADED);
        assertThat(record.getCategory()).isSameAs(category);
        assertThat(record.getSize()).isEqualTo(512L);
        assertThat(record.getUpdateTime()).isNotNull();
        assertThat(record.getLastSyncTime()).isNotNull();
        assertThat(record.getDeletable()).isTrue();
    }

    @Test
    void uploadSuccessWithArchiveUsesRemoteTimeForDeletablePolicy() {
        HiveRecord record = new HiveRecord();
        HiveOssObject object = object("key-1");
        object.setLastModified(new Date(0));

        stateService.startUpload(record, category(), "a.txt", "key-1");
        stateService.markUploaded(record, category(), object, CategoryStorageClass.ARCHIVE);

        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.UPLOADED);
        assertThat(record.getDeletable()).isTrue();
    }

    @Test
    void syncDoesNotMutatePendingDelete() {
        HiveRecord record = record(HiveRecordStatus.TO_BE_DELETED);
        record.setDeletable(false);

        assertThat(stateService.applyRemoteFound(record, category(), object("key-1"), CategoryStorageClass.STANDARD))
                .isEqualTo(HiveRecordStateService.SyncOutcome.SKIPPED);
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.TO_BE_DELETED);
        assertThat(record.getLastSyncTime()).isNull();
        assertThat(record.getDeletable()).isFalse();

        assertThat(stateService.applyRemoteMissing(record))
                .isEqualTo(HiveRecordStateService.SyncOutcome.SKIPPED);
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.TO_BE_DELETED);
        assertThat(record.getLastSyncTime()).isNull();
        assertThat(record.getDeletable()).isFalse();
    }

    @Test
    void syncTransitionsUploadedAndDbOnly() {
        HiveRecord uploaded = record(HiveRecordStatus.UPLOADED);
        assertThat(stateService.applyRemoteMissing(uploaded))
                .isEqualTo(HiveRecordStateService.SyncOutcome.DB_TO_OSS_MISSING);
        assertThat(uploaded.getStatus()).isEqualTo(HiveRecordStatus.DB_ONLY);
        assertThat(uploaded.getDeletable()).isFalse();

        assertThat(stateService.applyRemoteFound(uploaded, category(), object("key-1"), CategoryStorageClass.STANDARD))
                .isEqualTo(HiveRecordStateService.SyncOutcome.MATCHED);
        assertThat(uploaded.getStatus()).isEqualTo(HiveRecordStatus.UPLOADED);
        assertThat(uploaded.getDeletable()).isTrue();
    }

    @Test
    void ossOnlyNeverBecomesUploadedWhenRemoteExists() {
        HiveRecord record = stateService.discoverRemoteObject(category(), object("key-1"));
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.OSS_ONLY);

        assertThat(stateService.applyRemoteFound(record, category(), object("key-1"), CategoryStorageClass.STANDARD))
                .isEqualTo(HiveRecordStateService.SyncOutcome.OSS_ONLY);
        assertThat(record.getStatus()).isEqualTo(HiveRecordStatus.OSS_ONLY);
        assertThat(record.getDeletable()).isFalse();
    }

    @Test
    void cleanupLocalOnlyAllowsDbOnly() {
        HiveRecord dbOnly = record(HiveRecordStatus.DB_ONLY);
        stateService.cleanupLocalRecord(dbOnly);
        assertThat(dbOnly.getDeleted()).isTrue();
        assertThat(dbOnly.getCategory()).isNull();

        HiveRecord uploaded = record(HiveRecordStatus.UPLOADED);
        assertThatThrownBy(() -> stateService.cleanupLocalRecord(uploaded))
                .isInstanceOf(HiveRecordStateTransitionException.class)
                .hasMessageContaining("CLEANUP_LOCAL");
    }

    @Test
    void syncSkipsOperationStatesButFlagsFailedRecordFoundRemotely() {
        HiveRecord uploading = record(HiveRecordStatus.UPLOADING);
        assertThat(stateService.applyRemoteMissing(uploading))
                .isEqualTo(HiveRecordStateService.SyncOutcome.SKIPPED);
        assertThat(uploading.getStatus()).isEqualTo(HiveRecordStatus.UPLOADING);

        HiveRecord failedMissing = record(HiveRecordStatus.FAILED);
        assertThat(stateService.applyRemoteMissing(failedMissing))
                .isEqualTo(HiveRecordStateService.SyncOutcome.SKIPPED);
        assertThat(failedMissing.getStatus()).isEqualTo(HiveRecordStatus.FAILED);

        HiveRecord failedFound = record(HiveRecordStatus.FAILED);
        assertThat(stateService.applyRemoteFound(failedFound, category(), object("key-1"), CategoryStorageClass.STANDARD))
                .isEqualTo(HiveRecordStateService.SyncOutcome.OSS_TO_DB_MISMATCHED);
        assertThat(failedFound.getStatus()).isEqualTo(HiveRecordStatus.FAILED);
    }

    private HiveRecord record(HiveRecordStatus status) {
        HiveRecord record = new HiveRecord();
        record.setStatus(status);
        record.setCategory(category());
        record.setFileKey("key-1");
        record.setFileName("a.txt");
        record.setDeleted(false);
        return record;
    }

    private FileCategoryEntity category() {
        FileCategoryEntity category = new FileCategoryEntity();
        category.setId(1L);
        category.setCode("DOCS");
        category.setName("Docs");
        category.setStorageClass(CategoryStorageClass.STANDARD);
        category.setBucketName("bucket");
        return category;
    }

    private HiveOssObject object(String fileKey) {
        HiveOssObject object = new HiveOssObject();
        object.setFileKey(fileKey);
        object.setSize(128L);
        object.setLastModified(new Date());
        return object;
    }
}
