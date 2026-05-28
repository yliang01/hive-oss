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

    public List<FileGroupVO> listGroups(String categoryCode) {
        return fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode)
                .stream().map(this::toGroupVO).toList();
    }

    public Map<Integer, FileGroupRecord> findGroupRecordMapByRecordIds(Collection<Integer> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        return fileGroupRecordRepository.findByHiveRecord_IdIn(recordIds).stream()
                .collect(HashMap::new, (map, item) -> map.put(item.getHiveRecord().getId(), item), HashMap::putAll);
    }

    @Transactional
    public void assignFiles(Long categoryId, String categoryCode, String groupCode, List<String> fileKeys, String operator) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return;
        }
        FileGroup group = fileGroupRepository.findByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, groupCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found in category"));
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(fileKeys);
        if (normalizedKeys.isEmpty()) {
            return;
        }
        List<HiveRecord> records = hiveRecordRepository.findByCategory_IdAndFileKeyInAndDeletedIsFalse(categoryId, normalizedKeys);
        if (records.isEmpty()) {
            return;
        }
        List<Integer> recordIds = records.stream().map(HiveRecord::getId).toList();
        fileGroupRecordRepository.deleteByHiveRecord_IdIn(recordIds);
        String assignedBy = StringUtils.defaultIfBlank(operator, "system");
        LocalDateTime assignedAt = LocalDateTime.now();
        List<FileGroupRecord> links = records.stream().map(record -> {
            FileGroupRecord link = new FileGroupRecord();
            link.setGroup(group);
            link.setHiveRecord(record);
            link.setAssignedBy(assignedBy);
            link.setAssignedAt(assignedAt);
            return link;
        }).toList();
        fileGroupRecordRepository.saveAll(links);
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
        if (request.getSortOrder() == null) {
            throw new IllegalArgumentException("sortOrder is required");
        }
        group.setGroupName(request.getGroupName().trim());
        group.setSortOrder(request.getSortOrder());
        group.setUpdatedAt(LocalDateTime.now());
        return toGroupVO(fileGroupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(String groupCode, String categoryCode) {
        FileGroup group = fileGroupRepository.findByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, groupCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
        fileGroupRecordRepository.deleteByGroup_Id(group.getId());
        fileGroupRepository.delete(group);
    }

    private FileGroupVO toGroupVO(FileGroup group) {
        return FileGroupVO.builder()
                .categoryCode(group.getCategory().getCode())
                .groupCode(group.getGroupCode())
                .groupName(group.getGroupName())
                .sortOrder(group.getSortOrder())
                .build();
    }
}
