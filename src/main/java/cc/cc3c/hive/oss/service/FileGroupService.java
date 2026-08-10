package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.FileCategoryRepository;
import cc.cc3c.hive.domain.repository.FileGroupRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.FileGroupVO;
import cc.cc3c.hive.oss.controller.vo.GroupUpsertRequest;
import cc.cc3c.hive.oss.web.FileKeyNormalizer;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class FileGroupService {
    private final FileGroupRepository fileGroupRepository;
    private final HiveRecordRepository hiveRecordRepository;
    private final FileCategoryService fileCategoryService;

    public FileGroupService(FileGroupRepository fileGroupRepository,
                             HiveRecordRepository hiveRecordRepository,
                             FileCategoryService fileCategoryService) {
        this.fileGroupRepository = fileGroupRepository;
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileCategoryService = fileCategoryService;
    }

    public List<FileGroupVO> listGroups(String categoryCode) {
        return fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode)
                .stream().map(this::toGroupVO).toList();
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
        records.forEach(record -> record.assignTo(group));
        hiveRecordRepository.saveAll(records);
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
        group.setGroupCode(request.getGroupCode().trim().toUpperCase(Locale.ROOT));
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
        List<HiveRecord> records = hiveRecordRepository.findByGroup_Id(group.getId());
        records.forEach(HiveRecord::clearGroup);
        hiveRecordRepository.saveAll(records);
        hiveRecordRepository.flush();
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
