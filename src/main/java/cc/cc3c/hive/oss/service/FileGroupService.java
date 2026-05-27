package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.FileGroupRecord;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.FileGroupRecordRepository;
import cc.cc3c.hive.domain.repository.FileGroupRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.CategoryUpsertRequest;
import cc.cc3c.hive.oss.controller.vo.FileCategoryVO;
import cc.cc3c.hive.oss.controller.vo.FileGroupVO;
import cc.cc3c.hive.oss.controller.vo.GroupUpsertRequest;
import cc.cc3c.hive.oss.web.FileKeyNormalizer;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FileGroupService {
    private final FileCategoryRepository fileCategoryRepository;
    private final FileGroupRepository fileGroupRepository;
    private final FileGroupRecordRepository fileGroupRecordRepository;
    private final HiveRecordRepository hiveRecordRepository;
    private final FileCategoryService fileCategoryService;

    public FileGroupService(FileCategoryRepository fileCategoryRepository,
                            FileGroupRepository fileGroupRepository,
                            FileGroupRecordRepository fileGroupRecordRepository,
                            HiveRecordRepository hiveRecordRepository,
                            FileCategoryService fileCategoryService) {
        this.fileCategoryRepository = fileCategoryRepository;
        this.fileGroupRepository = fileGroupRepository;
        this.fileGroupRecordRepository = fileGroupRecordRepository;
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileCategoryService = fileCategoryService;
    }

    public List<FileGroupVO> listGroups(String categoryCode, boolean includeDisabled) {
        List<FileGroup> groups = includeDisabled
                ? fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode)
                : fileGroupRepository.findByCategory_CodeAndEnabledTrueOrderBySortOrderAscIdAsc(categoryCode);
        return groups.stream().map(this::toGroupVO).toList();
    }

    public Map<Integer, FileGroupRecord> findGroupRecordMapByRecordIds(Collection<Integer> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        return fileGroupRecordRepository.findByHiveRecord_IdIn(recordIds).stream()
                .collect(HashMap::new, (map, item) -> map.put(item.getHiveRecord().getId(), item), HashMap::putAll);
    }

    @Transactional
    public void assignFiles(String categoryCode, Long groupId, List<String> fileKeys, String operator) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return;
        }
        FileGroup group = fileGroupRepository.findByIdAndCategory_CodeAndEnabledTrue(groupId, categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found in category"));
        FileCategoryEntity category = fileCategoryService.resolveCategory(categoryCode);
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(fileKeys);
        if (normalizedKeys.isEmpty()) {
            return;
        }
        List<HiveRecord> records = hiveRecordRepository.findByCategory_IdAndFileKeyInAndDeletedIsFalse(category.getId(), normalizedKeys);
        String assignedBy = StringUtils.defaultIfBlank(operator, "system");
        for (HiveRecord record : records) {
            Optional<FileGroupRecord> existing = fileGroupRecordRepository.findByHiveRecord_IdAndGroup_Category_Code(record.getId(), categoryCode);
            FileGroupRecord link = existing.orElseGet(FileGroupRecord::new);
            link.setGroup(group);
            link.setHiveRecord(record);
            link.setAssignedBy(assignedBy);
            link.setAssignedAt(LocalDateTime.now());
            fileGroupRecordRepository.save(link);
        }
    }

    @Transactional
    public FileGroupVO createGroup(String categoryCode, GroupUpsertRequest request) {
        if (StringUtils.isBlank(request.getGroupCode()) || StringUtils.isBlank(request.getGroupName())) {
            throw new IllegalArgumentException("groupCode and groupName are required");
        }
        if (fileGroupRepository.existsByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, request.getGroupCode())) {
            throw new IllegalArgumentException("group code exists in category");
        }
        FileCategoryEntity category = fileCategoryService.resolveCategory(categoryCode);
        FileGroup group = new FileGroup();
        group.setCategory(category);
        group.setGroupCode(request.getGroupCode().trim());
        group.setGroupName(request.getGroupName().trim());
        group.setGroupDesc(StringUtils.trimToNull(request.getGroupDesc()));
        group.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        group.setEnabled(request.getEnabled() == null || request.getEnabled());
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        return toGroupVO(fileGroupRepository.save(group));
    }

    @Transactional
    public FileGroupVO updateGroup(Long groupId, String categoryCode, GroupUpsertRequest request) {
        FileGroup group = fileGroupRepository.findByIdAndCategory_Code(groupId, categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
        if (StringUtils.isNotBlank(request.getGroupCode()) && !StringUtils.equalsIgnoreCase(group.getGroupCode(), request.getGroupCode())) {
            if (fileGroupRepository.existsByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, request.getGroupCode())) {
                throw new IllegalArgumentException("group code exists in category");
            }
            group.setGroupCode(request.getGroupCode().trim());
        }
        if (StringUtils.isNotBlank(request.getGroupName())) {
            group.setGroupName(request.getGroupName().trim());
        }
        group.setGroupDesc(StringUtils.trimToNull(request.getGroupDesc()));
        if (request.getSortOrder() != null) {
            group.setSortOrder(request.getSortOrder());
        }
        if (request.getEnabled() != null) {
            group.setEnabled(request.getEnabled());
        }
        group.setUpdatedAt(LocalDateTime.now());
        return toGroupVO(fileGroupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(Long groupId, String categoryCode) {
        FileGroup group = fileGroupRepository.findByIdAndCategory_Code(groupId, categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
        long relationCount = fileGroupRecordRepository.countByGroup_Id(groupId);
        if (relationCount > 0) {
            throw new IllegalStateException("group still has linked files");
        }
        fileGroupRepository.delete(group);
    }

    private FileGroupVO toGroupVO(FileGroup group) {
        return FileGroupVO.builder()
                .groupId(group.getId())
                .categoryCode(group.getCategory().getCode())
                .groupCode(group.getGroupCode())
                .groupName(group.getGroupName())
                .groupDesc(group.getGroupDesc())
                .sortOrder(group.getSortOrder())
                .enabled(group.getEnabled())
                .build();
    }
}
