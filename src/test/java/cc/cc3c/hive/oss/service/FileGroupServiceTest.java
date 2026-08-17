package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.FileGroupRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileGroupServiceTest {

    @Test
    void rejectsAssignmentWhenARecordHasALocalDownload() {
        FileGroupRepository groupRepository = mock(FileGroupRepository.class);
        HiveRecordRepository recordRepository = mock(HiveRecordRepository.class);
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        FileGroupService service = new FileGroupService(groupRepository, recordRepository,
                mock(FileCategoryService.class), downloadService);
        FileGroup sourceGroup = group(1L, "SOURCE");
        FileGroup targetGroup = group(2L, "TARGET");
        HiveRecord record = record(sourceGroup);

        when(groupRepository.findByCategory_CodeAndGroupCodeIgnoreCase("DOCS", "TARGET")).thenReturn(Optional.of(targetGroup));
        when(recordRepository.findWithGroupByCategory_IdAndFileKeyInAndDeletedIsFalse(1L, List.of("file-key")))
                .thenReturn(List.of(record));
        when(downloadService.hasLocalDownloadOrIsDownloading("DOCS", record)).thenReturn(true);

        assertThatThrownBy(() -> service.assignFiles(1L, "DOCS", "TARGET", List.of("file-key"), "ui"))
                .isInstanceOf(LocalDownloadGroupConflictException.class)
                .hasMessageContaining("无法调整分组");

        verify(recordRepository, never()).saveAll(any());
    }

    @Test
    void rejectsGroupDeletionWhenARecordIsDownloading() {
        FileGroupRepository groupRepository = mock(FileGroupRepository.class);
        HiveRecordRepository recordRepository = mock(HiveRecordRepository.class);
        HiveDownloadService downloadService = mock(HiveDownloadService.class);
        FileGroupService service = new FileGroupService(groupRepository, recordRepository,
                mock(FileCategoryService.class), downloadService);
        FileGroup group = group(1L, "SOURCE");
        HiveRecord record = record(group);

        when(groupRepository.findByCategory_CodeAndGroupCodeIgnoreCase("DOCS", "SOURCE")).thenReturn(Optional.of(group));
        when(recordRepository.findByGroup_Id(1L)).thenReturn(List.of(record));
        when(downloadService.hasLocalDownloadOrIsDownloading("DOCS", record)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteGroup("SOURCE", "DOCS"))
                .isInstanceOf(LocalDownloadGroupConflictException.class)
                .hasMessageContaining("无法删除分组");

        verify(groupRepository, never()).delete(any());
    }

    @Test
    private FileGroup group(Long id, String code) {
        FileGroup group = new FileGroup();
        group.setId(id);
        group.setGroupCode(code);
        return group;
    }

    private HiveRecord record(FileGroup group) {
        HiveRecord record = new HiveRecord();
        record.setFileKey("file-key");
        record.setFileName("example.txt");
        record.assignTo(group);
        return record;
    }
}
