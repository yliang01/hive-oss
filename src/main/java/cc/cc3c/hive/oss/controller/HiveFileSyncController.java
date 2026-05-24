package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.FileGroupRecordRepository;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.FileGroupAssignRequest;
import cc.cc3c.hive.oss.controller.vo.HiveSyncVO;
import cc.cc3c.hive.oss.service.FileCategoryResolver;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.service.HiveSyncService;
import cc.cc3c.hive.oss.thumbnail.ThumbnailAsyncService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
public class HiveFileSyncController {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final FileGroupRecordRepository fileGroupRecordRepository;
    private final FileGroupService fileGroupService;
    private final HiveSyncService hiveSyncService;
    private final HiveOssService hiveOssService;
    private final FileCategoryResolver fileCategoryResolver;
    private final ThumbnailAsyncService thumbnailAsyncService;

    public HiveFileSyncController(HiveRecordRepository hiveRecordRepository,
                                   HiveRecordImageMetaRepository imageMetaRepository,
                                   FileGroupRecordRepository fileGroupRecordRepository,
                                   FileGroupService fileGroupService,
                                   HiveSyncService hiveSyncService,
                                   HiveOssService hiveOssService,
                                   FileCategoryResolver fileCategoryResolver,
                                   ThumbnailAsyncService thumbnailAsyncService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.fileGroupRecordRepository = fileGroupRecordRepository;
        this.fileGroupService = fileGroupService;
        this.hiveSyncService = hiveSyncService;
        this.hiveOssService = hiveOssService;
        this.fileCategoryResolver = fileCategoryResolver;
        this.thumbnailAsyncService = thumbnailAsyncService;
    }

    @PostMapping("/categories/{category}/files/sync-remote")
    public ResponseEntity<HiveSyncVO> syncRemote(@PathVariable("category") String category) {
        try {
            HiveSyncVO hiveSyncVO = hiveSyncService.syncRemote(fileCategoryResolver.resolveCategory(category));
            return ResponseEntity.ok(hiveSyncVO);
        } catch (Exception e) {
            log.error("sync remote failed {}", category, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/categories/{category}/files/confirm-delete")
    public ResponseEntity<Void> confirmDelete(@PathVariable("category") String category) {
        String bucketName = fileGroupService.resolveBucket(category);
        List<HiveRecord> recordList = hiveRecordRepository.findByStatusAndDeletedIsFalse(HiveRecordStatus.TO_BE_DELETED);
        for (HiveRecord record : recordList) {
            if (!StringUtils.equals(record.getBucketName(), bucketName)) {
                continue;
            }
            HiveRecordImageMeta meta = imageMetaRepository.findByHiveRecordId(record.getId()).orElse(null);
            if (meta != null && StringUtils.isNotBlank(meta.getThumbKey())) {
                HiveOssTask thumbTask = HiveOssTask.createTask()
                        .withBucket(record.getBucketName())
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
                    .withBucket(record.getBucketName())
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
