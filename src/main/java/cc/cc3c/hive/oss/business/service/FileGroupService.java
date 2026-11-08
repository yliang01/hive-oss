package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.FileGroupRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.business.controller.vo.FileGroupVO;
import cc.cc3c.hive.oss.business.controller.vo.GroupUpsertRequest;
import cc.cc3c.hive.oss.business.web.FileKeyNormalizer;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class FileGroupService {
    private static final String UNGROUPED_DIR = "_ungrouped";

    private final FileGroupRepository fileGroupRepository;
    private final HiveRecordRepository hiveRecordRepository;
    private final FileCategoryService fileCategoryService;
    private final HiveDownloadService hiveDownloadService;

    public FileGroupService(FileGroupRepository fileGroupRepository,
                              HiveRecordRepository hiveRecordRepository,
                              FileCategoryService fileCategoryService,
                              HiveDownloadService hiveDownloadService) {
        this.fileGroupRepository = fileGroupRepository;
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileCategoryService = fileCategoryService;
        this.hiveDownloadService = hiveDownloadService;
    }

    public List<FileGroupVO> listGroups(String categoryCode) {
        return fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode)
                .stream().map(this::toGroupVO).toList();
    }

    @Transactional
    public void assignFiles(Long categoryId, String categoryCode, String groupCode, List<String> fileKeys) {
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(fileKeys);
        if (normalizedKeys.isEmpty()) {
            throw new IllegalArgumentException("fileKeys are required");
        }
        FileGroup group = StringUtils.isBlank(groupCode) ? null : fileGroupRepository
                .findByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, groupCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found in category"));
        List<HiveRecord> records = hiveRecordRepository.findWithGroupByCategory_IdAndFileKeyInAndDeletedIsFalse(categoryId, normalizedKeys);
        if (records.size() != normalizedKeys.size()) {
            throw new IllegalArgumentException("some files were not found in category");
        }
        List<HiveRecord> changedRecords = records.stream()
                .filter(record -> !sameGroup(record.getGroup(), group))
                .toList();
        String categoryName = StringUtils.defaultIfBlank(fileCategoryService.nameByCode(categoryCode), categoryCode);
        moveAndAssign(categoryCode, categoryName, changedRecords, group,
                "存在下载中的文件，无法调整分组", null);
    }

    @Transactional
    public FileGroupVO createGroup(String categoryCode, GroupUpsertRequest request) {
        if (StringUtils.isBlank(request.getGroupCode()) || StringUtils.isBlank(request.getGroupName())) {
            throw new IllegalArgumentException("groupCode and groupName are required");
        }
        String groupName = request.getGroupName().trim();
        if (UNGROUPED_DIR.equalsIgnoreCase(groupName)) {
            throw new IllegalArgumentException("groupName is reserved");
        }
        if (fileGroupRepository.existsByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, request.getGroupCode())) {
            throw new IllegalArgumentException("group code exists in category");
        }
        FileCategoryEntity category = fileCategoryService.resolveCategory(categoryCode);
        if (fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode).stream()
                .anyMatch(existing -> existing.getGroupName() != null && existing.getGroupName().equalsIgnoreCase(groupName))) {
            throw new IllegalArgumentException("group name exists in category");
        }
        FileGroup group = new FileGroup();
        group.setCategory(category);
        group.setGroupCode(request.getGroupCode().trim().toUpperCase(Locale.ROOT));
        group.setGroupName(groupName);
        group.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        return toGroupVO(fileGroupRepository.save(group));
    }

    @Transactional
    public FileGroupVO updateGroup(String groupCode, String categoryCode, GroupUpsertRequest request) {
        FileGroup group = fileGroupRepository.findByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, groupCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
        if (StringUtils.isBlank(request.getGroupName())) {
            throw new IllegalArgumentException("groupName is required");
        }
        String groupName = request.getGroupName().trim();
        if (UNGROUPED_DIR.equalsIgnoreCase(groupName)) {
            throw new IllegalArgumentException("groupName is reserved");
        }
        if (fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode).stream()
                .anyMatch(existing -> !existing.getId().equals(group.getId())
                        && existing.getGroupName() != null && existing.getGroupName().equalsIgnoreCase(groupName))) {
            throw new IllegalArgumentException("group name exists in category");
        }
        if (request.getSortOrder() == null) {
            throw new IllegalArgumentException("sortOrder is required");
        }
        group.setGroupName(groupName);
        group.setSortOrder(request.getSortOrder());
        group.setUpdatedAt(LocalDateTime.now());
        return toGroupVO(fileGroupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(String groupCode, String categoryCode) {
        FileGroup group = fileGroupRepository.findByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, groupCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
        List<HiveRecord> records = hiveRecordRepository.findByGroup_Id(group.getId());
        String categoryName = StringUtils.defaultIfBlank(fileCategoryService.nameByCode(categoryCode), categoryCode);
        moveAndAssign(categoryCode, categoryName, records, null,
                "分组中存在下载中的文件，无法删除分组", () -> {
            fileGroupRepository.delete(group);
            fileGroupRepository.flush();
        });
    }

    private FileGroupVO toGroupVO(FileGroup group) {
        return FileGroupVO.builder()
                .categoryCode(group.getCategory().getCode())
                .groupCode(group.getGroupCode())
                .groupName(group.getGroupName())
                .sortOrder(group.getSortOrder())
                .build();
    }

    private void moveAndAssign(String categoryCode, String categoryName, List<HiveRecord> records, FileGroup targetGroup,
                               String downloadingMessage, Runnable afterAssignment) {
        try {
            hiveDownloadService.coordinateLocalFileOperation(() -> {
                ensureNotDownloading(categoryCode, records, downloadingMessage);
                List<HiveDownloadService.LocalDownloadMove> moves = new ArrayList<>();
                try {
                    String targetGroupName = targetGroup == null ? null : targetGroup.getGroupName();
                    for (HiveRecord record : records) {
                        HiveDownloadService.LocalDownloadMove move = hiveDownloadService.moveLocalDownload(categoryName, record, targetGroupName);
                        if (move != null) {
                            moves.add(move);
                        }
                    }
                    records.forEach(record -> {
                        if (targetGroup == null) {
                            record.clearGroup();
                        } else {
                            record.assignTo(targetGroup);
                        }
                    });
                    hiveRecordRepository.saveAll(records);
                    hiveRecordRepository.flush();
                    if (afterAssignment != null) {
                        afterAssignment.run();
                    }
                    return null;
                } catch (Exception e) {
                    restoreMoves(moves, e);
                    throw e;
                }
            });
        } catch (LocalDownloadGroupConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("移动本地文件并调整分组失败", e);
        }
    }

    private void ensureNotDownloading(String categoryCode, List<HiveRecord> records, String message) {
        if (records.stream().anyMatch(record -> hiveDownloadService.isDownloading(categoryCode, record))) {
            throw new LocalDownloadGroupConflictException(message);
        }
    }

    private void restoreMoves(List<HiveDownloadService.LocalDownloadMove> moves, Exception cause) {
        for (int index = moves.size() - 1; index >= 0; index--) {
            try {
                hiveDownloadService.restoreLocalDownloadMove(moves.get(index));
            } catch (IOException rollbackFailure) {
                cause.addSuppressed(rollbackFailure);
            }
        }
    }

    private boolean sameGroup(FileGroup first, FileGroup second) {
        return first == second || first != null && second != null && Objects.equals(first.getId(), second.getId());
    }
}
