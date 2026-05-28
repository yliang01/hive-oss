package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.FileGroupRecord;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.FileGroupRecordRepository;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.FileBulkMarkDeleteRequest;
import cc.cc3c.hive.oss.controller.vo.HiveRecordVO;
import cc.cc3c.hive.oss.controller.vo.HiveRecordsVO;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.service.FileQueryParams;
import cc.cc3c.hive.oss.service.HiveRecordQueryService;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.thumbnail.ThumbnailAsyncService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.web.CategoryContext;
import cc.cc3c.hive.oss.web.FileKeyNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class HiveFileController {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final HiveRecordQueryService hiveRecordQueryService;
    private final FileGroupService fileGroupService;
    private final HiveRecordVOAssembler assembler;
    private final FileGroupRecordRepository fileGroupRecordRepository;
    private final HiveOssService hiveOssService;
    private final ThumbnailAsyncService thumbnailAsyncService;

    public HiveFileController(HiveRecordRepository hiveRecordRepository, HiveRecordImageMetaRepository imageMetaRepository, HiveRecordQueryService hiveRecordQueryService, FileGroupService fileGroupService, HiveRecordVOAssembler assembler, FileGroupRecordRepository fileGroupRecordRepository, HiveOssService hiveOssService, ThumbnailAsyncService thumbnailAsyncService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.hiveRecordQueryService = hiveRecordQueryService;
        this.fileGroupService = fileGroupService;
        this.assembler = assembler;
        this.fileGroupRecordRepository = fileGroupRecordRepository;
        this.hiveOssService = hiveOssService;
        this.thumbnailAsyncService = thumbnailAsyncService;
    }

    @GetMapping("/categories/{category}/files:batch-get")
    public ResponseEntity<List<HiveRecordVO>> batchGetFiles(CategoryContext category,
                                                            @RequestParam("fileKeys") List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(fileKeys);
        if (normalizedKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<HiveRecord> records = hiveRecordRepository
                .findByCategory_IdAndFileKeyInAndDeletedIsFalse(category.categoryId(), normalizedKeys);
        return ResponseEntity.ok(buildRecordVOs(category, records));
    }

    @GetMapping("/categories/{category}/files")
    public HiveRecordsVO getFiles(CategoryContext category,
                                  @RequestParam(value = "groupCode", required = false) String groupCode,
                                  @RequestParam(value = "ungroupedOnly", required = false) Boolean ungroupedOnly,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("pageSize") Integer pageSize) {
        Long categoryId = category.categoryId();
        Page<HiveRecord> pageResult = hiveRecordQueryService.query(categoryId,
                new FileQueryParams(groupCode, Boolean.TRUE.equals(ungroupedOnly), keyword, page, pageSize));

        List<HiveRecordVO> list = buildRecordVOs(category, pageResult.stream().toList());
        return HiveRecordsVO.builder().files(list).total((int) pageResult.getTotalElements()).page(page).pageSize(pageSize).build();
    }

    private List<HiveRecordVO> buildRecordVOs(CategoryContext context, List<HiveRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Integer> recordIds = records.stream().map(HiveRecord::getId).toList();
        Map<Integer, FileGroupRecord> groupRecordMap =
                fileGroupService.findGroupRecordMapByRecordIds(recordIds);
        Map<Integer, HiveRecordImageMeta> imageMetaMap = imageMetaRepository.findByHiveRecordIdIn(recordIds).stream()
                .collect(Collectors.toMap(HiveRecordImageMeta::getHiveRecordId, m -> m));
        return records.stream()
                .map(record -> assembler.buildHiveRecordVO(record, context,
                        groupRecordMap.get(record.getId()),
                        Optional.ofNullable(imageMetaMap.get(record.getId()))))
                .toList();
    }

    @PostMapping("/categories/{category}/files:mark-delete")
    public ResponseEntity<Void> bulkMarkDelete(CategoryContext category,
                                               @RequestBody FileBulkMarkDeleteRequest request) {
        if (request.getFileKeys() == null || request.getFileKeys().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(request.getFileKeys());
        if (normalizedKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<HiveRecord> records = hiveRecordRepository
                .findByCategory_IdAndFileKeyInAndDeletedIsFalse(category.categoryId(), normalizedKeys);
        for (HiveRecord record : records) {
            record.setStatus(HiveRecordStatus.TO_BE_DELETED);
            record.setDeletable(false);
        }
        hiveRecordRepository.saveAll(records);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/categories/{category}/files/confirm-delete")
    public ResponseEntity<Void> confirmDelete(CategoryContext category) {
        List<HiveRecord> recordList = hiveRecordRepository.findByCategory_IdAndStatusAndDeletedIsFalse(
                category.categoryId(), HiveRecordStatus.TO_BE_DELETED);
        for (HiveRecord record : recordList) {
            HiveRecordImageMeta meta = imageMetaRepository.findByHiveRecordId(record.getId()).orElse(null);
            if (meta != null && StringUtils.isNotBlank(meta.getThumbKey())) {
                HiveOssTask thumbTask = HiveOssTask.createTask()
                        .withBucket(record.getCategory().getBucketName())
                        .withKey(meta.getThumbKey());
                try {
                    hiveOssService.using(record.getProvider()).delete(thumbTask);
                } catch (Exception e) {
                    log.error("failed to delete thumbnail {}", thumbTask, e);
                    return ResponseEntity.internalServerError().build();
                }
            }
            if (meta != null) {
                imageMetaRepository.delete(meta);
            }
            HiveOssTask hiveOssTask = HiveOssTask.createTask()
                    .withBucket(record.getCategory().getBucketName())
                    .withKey(record.getFileKey());
            try {
                hiveOssService.using(record.getProvider()).delete(hiveOssTask);
            } catch (Exception e) {
                log.error("failed to delete {}", hiveOssTask);
                return ResponseEntity.internalServerError().build();
            }
            fileGroupRecordRepository.deleteByHiveRecord_Id(record.getId());
            thumbnailAsyncService.deleteCacheFiles(record.getFileKey());
            record.setDeleted(true);
            hiveRecordRepository.save(record);
        }
        return ResponseEntity.ok().build();
    }
}
