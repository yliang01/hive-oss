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
    private final FileCategoryResolver fileCategoryResolver;

    public FileGroupService(FileCategoryRepository fileCategoryRepository,
                            FileGroupRepository fileGroupRepository,
                            FileGroupRecordRepository fileGroupRecordRepository,
                            HiveRecordRepository hiveRecordRepository,
                            FileCategoryResolver fileCategoryResolver) {
        this.fileCategoryRepository = fileCategoryRepository;
        this.fileGroupRepository = fileGroupRepository;
        this.fileGroupRecordRepository = fileGroupRecordRepository;
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileCategoryResolver = fileCategoryResolver;
    }

    public List<FileCategoryVO> listCategories() {
        return fileCategoryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::toCategoryVO)
                .toList();
    }

    public List<FileCategoryVO> listCategoriesForAdmin() {
        return fileCategoryRepository.findAll().stream()
                .sorted(Comparator.comparing(FileCategoryEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(FileCategoryEntity::getId))
                .map(this::toCategoryVO)
                .toList();
    }

    public List<FileGroupVO> listGroups(String categoryCode) {
        return fileGroupRepository.findByCategory_CodeAndEnabledTrueOrderBySortOrderAscIdAsc(categoryCode)
                .stream()
                .map(this::toGroupVO)
                .toList();
    }

    public List<FileGroupVO> listGroupsForAdmin(String categoryCode) {
        return fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode).stream()
                .map(this::toGroupVO)
                .toList();
    }

    public Set<Integer> resolveRecordIdsForQuery(String categoryCode, Long groupId) {
        List<Integer> ids;
        if (groupId != null) {
            ids = fileGroupRecordRepository.findHiveRecordIdsByGroupIdAndCategoryCode(groupId, categoryCode);
        } else {
            ids = fileGroupRecordRepository.findHiveRecordIdsByCategoryCode(categoryCode);
        }
        return new HashSet<>(ids);
    }

    public Map<Integer, FileGroupRecord> findGroupRecordMapByRecordIds(Collection<Integer> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        return fileGroupRecordRepository.findByHiveRecord_IdIn(recordIds).stream()
                .collect(HashMap::new, (map, item) -> map.put(item.getHiveRecord().getId(), item), HashMap::putAll);
    }

    public FileCategoryEntity resolveCategory(String categoryCode) {
        return fileCategoryResolver.resolveCategory(categoryCode);
    }

    public String resolveBucket(String categoryCode) {
        return fileCategoryResolver.resolveBucket(categoryCode);
    }

    @Transactional
    public void assignFiles(String categoryCode, Long groupId, List<String> fileKeys, String operator) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return;
        }
        FileGroup group = fileGroupRepository.findByIdAndCategory_CodeAndEnabledTrue(groupId, categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("group not found in category"));
        String bucketName = resolveBucket(categoryCode);
        List<String> normalizedKeys = fileKeys.stream().filter(StringUtils::isNotBlank).distinct().toList();
        List<HiveRecord> records = hiveRecordRepository.findByBucketNameAndFileKeyInAndDeletedIsFalse(bucketName, normalizedKeys);
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
    public FileCategoryVO createCategory(CategoryUpsertRequest request) {
        if (StringUtils.isBlank(request.getCode()) || StringUtils.isBlank(request.getName())) {
            throw new IllegalArgumentException("code and name are required");
        }
        if (fileCategoryRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new IllegalArgumentException("category code exists");
        }
        FileCategoryEntity entity = new FileCategoryEntity();
        entity.setCode(request.getCode().trim().toUpperCase(Locale.ROOT));
        fillCategory(entity, request);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setSystem(Boolean.TRUE.equals(request.getSystem()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return toCategoryVO(fileCategoryRepository.save(entity));
    }

    @Transactional
    public FileCategoryVO updateCategory(String categoryCode, CategoryUpsertRequest request) {
        FileCategoryEntity entity = fileCategoryResolver.resolveCategory(categoryCode);
        fillCategory(entity, request);
        entity.setUpdatedAt(LocalDateTime.now());
        return toCategoryVO(fileCategoryRepository.save(entity));
    }

    @Transactional
    public void disableCategory(String categoryCode) {
        FileCategoryEntity entity = fileCategoryResolver.resolveCategory(categoryCode);
        entity.setEnabled(false);
        entity.setUpdatedAt(LocalDateTime.now());
        fileCategoryRepository.save(entity);
    }

    @Transactional
    public void deleteCategory(String categoryCode) {
        FileCategoryEntity entity = fileCategoryResolver.resolveCategory(categoryCode);
        if (Boolean.TRUE.equals(entity.getSystem())) {
            throw new IllegalArgumentException("system category cannot be deleted");
        }
        long groupCount = fileGroupRepository.findByCategory_CodeOrderBySortOrderAscIdAsc(categoryCode).size();
        if (groupCount > 0) {
            throw new IllegalStateException("category still has groups");
        }
        fileCategoryRepository.delete(entity);
    }

    @Transactional
    public FileGroupVO createGroup(String categoryCode, GroupUpsertRequest request) {
        if (StringUtils.isBlank(request.getGroupCode()) || StringUtils.isBlank(request.getGroupName())) {
            throw new IllegalArgumentException("groupCode and groupName are required");
        }
        if (fileGroupRepository.existsByCategory_CodeAndGroupCodeIgnoreCase(categoryCode, request.getGroupCode())) {
            throw new IllegalArgumentException("group code exists in category");
        }
        FileCategoryEntity category = fileCategoryResolver.resolveCategory(categoryCode);
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

    private FileCategoryVO toCategoryVO(FileCategoryEntity category) {
        return FileCategoryVO.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .bucketName(category.getBucketName())
                .storageClass(category.getStorageClass() == null ? null : category.getStorageClass().name())
                .previewPolicy(category.getPreviewPolicy())
                .uiVariant(category.getUiVariant())
                .enabled(category.getEnabled())
                .sortOrder(category.getSortOrder())
                .system(category.getSystem())
                .build();
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

    private void fillCategory(FileCategoryEntity entity, CategoryUpsertRequest request) {
        entity.setName(StringUtils.defaultIfBlank(request.getName(), entity.getName()));
        entity.setDescription(StringUtils.trimToNull(request.getDescription()));
        String bucketName = StringUtils.trimToNull(request.getBucketName());
        if (bucketName != null) {
            if (entity.getId() == null) {
                if (fileCategoryRepository.existsByBucketNameIgnoreCase(bucketName)) {
                    throw new IllegalArgumentException("bucket name exists");
                }
            } else if (fileCategoryRepository.existsByBucketNameIgnoreCaseAndIdNot(bucketName, entity.getId())) {
                throw new IllegalArgumentException("bucket name exists");
            }
            entity.setBucketName(bucketName);
        } else if (StringUtils.isBlank(entity.getBucketName())) {
            throw new IllegalArgumentException("bucketName is required");
        }
        if (StringUtils.isNotBlank(request.getStorageClass())) {
            entity.setStorageClass(CategoryStorageClass.valueOf(request.getStorageClass().trim().toUpperCase(Locale.ROOT)));
        } else if (entity.getStorageClass() == null) {
            entity.setStorageClass(CategoryStorageClass.STANDARD);
        }
        entity.setPreviewPolicy(StringUtils.defaultIfBlank(request.getPreviewPolicy(), "DEFAULT"));
        entity.setUiVariant(StringUtils.defaultIfBlank(request.getUiVariant(), "hot"));
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        } else if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (request.getSystem() != null) {
            entity.setSystem(request.getSystem());
        } else if (entity.getSystem() == null) {
            entity.setSystem(false);
        }
    }
}
