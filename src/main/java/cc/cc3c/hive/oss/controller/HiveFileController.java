package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.FileBulkMarkDeleteRequest;
import cc.cc3c.hive.oss.controller.vo.HiveRecordVO;
import cc.cc3c.hive.oss.controller.vo.HiveRecordsVO;
import cc.cc3c.hive.oss.service.FileGroupService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class HiveFileController {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final FileGroupService fileGroupService;
    private final HiveRecordVOAssembler assembler;

    public HiveFileController(HiveRecordRepository hiveRecordRepository,
                              HiveRecordImageMetaRepository imageMetaRepository,
                              FileGroupService fileGroupService,
                              HiveRecordVOAssembler assembler) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.fileGroupService = fileGroupService;
        this.assembler = assembler;
    }

    @GetMapping("/categories/{category}/files/{*fileKey}")
    public ResponseEntity<HiveRecordVO> getFile(@PathVariable("category") String category,
                                                @PathVariable("fileKey") String fileKey) {
        fileKey = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord record = optional.get();
        Optional<HiveRecordImageMeta> imageMeta = imageMetaRepository.findByHiveRecordId(record.getId());
        return ResponseEntity.ok(assembler.buildHiveRecordVO(record, category, null, imageMeta));
    }

    @GetMapping("/categories/{category}/files")
    public HiveRecordsVO getFiles(@PathVariable("category") String category,
                                  @RequestParam(value = "groupId", required = false) Long groupId,
                                  @RequestParam(value = "ungroupedOnly", required = false) Boolean ungroupedOnly,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("pageSize") Integer pageSize) {
        return queryFiles(category, groupId, Boolean.TRUE.equals(ungroupedOnly), keyword, page, pageSize);
    }

    @PostMapping("/categories/{category}/files:mark-delete")
    public ResponseEntity<Void> bulkMarkDelete(@PathVariable("category") String category,
                                               @RequestBody FileBulkMarkDeleteRequest request) {
        if (request.getFileKeys() == null || request.getFileKeys().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String bucketName = fileGroupService.resolveBucket(category);
        List<HiveRecord> records = hiveRecordRepository
                .findByBucketNameAndFileKeyInAndDeletedIsFalse(bucketName, request.getFileKeys());
        for (HiveRecord record : records) {
            record.setStatus(HiveRecordStatus.TO_BE_DELETED);
            record.setDeletable(false);
        }
        hiveRecordRepository.saveAll(records);
        return ResponseEntity.ok().build();
    }

    private HiveRecordsVO queryFiles(String category, Long groupId, boolean ungroupedOnly,
                                     String keyword, Integer page, Integer pageSize) {
        String bucketName = fileGroupService.resolveBucket(category);
        String normalizedKeyword = StringUtils.trimToNull(keyword);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("id").descending());
        Page<HiveRecord> pageResult;

        if (groupId != null) {
            Set<Integer> ids = fileGroupService.resolveRecordIdsForQuery(category, groupId);
            if (ids.isEmpty()) {
                return HiveRecordsVO.builder().files(List.of()).total(0).page(page).pageSize(pageSize).build();
            }
            if (normalizedKeyword != null) {
                pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdInAndFileNameContainingIgnoreCase(pageable, bucketName, ids, normalizedKeyword);
            } else {
                pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdIn(pageable, bucketName, ids);
            }
        } else if (ungroupedOnly) {
            Set<Integer> groupedIds = fileGroupService.resolveRecordIdsForQuery(category, null);
            if (groupedIds.isEmpty()) {
                if (normalizedKeyword != null) {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndFileNameContainingIgnoreCase(pageable, bucketName, normalizedKeyword);
                } else {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalse(pageable, bucketName);
                }
            } else {
                if (normalizedKeyword != null) {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdNotInAndFileNameContainingIgnoreCase(pageable, bucketName, groupedIds, normalizedKeyword);
                } else {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdNotIn(pageable, bucketName, groupedIds);
                }
            }
        } else if (normalizedKeyword != null) {
            pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndFileNameContainingIgnoreCase(pageable, bucketName, normalizedKeyword);
        } else {
            pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalse(pageable, bucketName);
        }

        Map<Integer, cc.cc3c.hive.domain.entity.FileGroupRecord> groupRecordMap =
                fileGroupService.findGroupRecordMapByRecordIds(pageResult.stream().map(HiveRecord::getId).toList());
        List<Integer> recordIds = pageResult.stream().map(HiveRecord::getId).toList();
        Map<Integer, HiveRecordImageMeta> imageMetaMap = imageMetaRepository.findByHiveRecordIdIn(recordIds).stream()
                .collect(Collectors.toMap(HiveRecordImageMeta::getHiveRecordId, m -> m));
        List<HiveRecordVO> list = pageResult.stream()
                .map(record -> assembler.buildHiveRecordVO(record, category,
                        groupRecordMap.get(record.getId()),
                        Optional.ofNullable(imageMetaMap.get(record.getId()))))
                .toList();
        return HiveRecordsVO.builder().files(list).total((int) pageResult.getTotalElements()).page(page).pageSize(pageSize).build();
    }
}
