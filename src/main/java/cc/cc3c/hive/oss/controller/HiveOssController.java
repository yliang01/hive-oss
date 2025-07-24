package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveRecordVO;
import cc.cc3c.hive.oss.controller.vo.HiveRecordsVO;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.service.HiveSyncService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
public class HiveOssController {

    @Autowired
    private HiveOssService hiveOssService;
    @Autowired
    private HiveSyncService hiveSyncService;
    @Autowired
    private HiveRecordRepository hiveRecordRepository;


    @GetMapping("/buckets")
    public List<String> getBuckets() {
        return Arrays.stream(HiveRecordSource.values()).map(x -> x.name()).toList();
    }

    @GetMapping("/buckets/{bucket}/files")
    public HiveRecordsVO getFiles(@PathVariable("bucket") HiveRecordSource source,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("pageSize") Integer pageSize) {

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("id").descending());
        Page<HiveRecord> pageResult = hiveRecordRepository.findBySourceAndDeletedIsFalse(pageable, source);

        List<HiveRecordVO> list = pageResult.stream().map(x -> {
            return HiveRecordVO.builder().fileName(x.getFileName())
                    .fileKey(x.getFileKey())
                    .zipped(x.getZipped())
                    .size(x.getSize())
                    .status(x.getStatus().name())
                    .updateTime(x.getUpdateTime())
                    .deletable(x.getDeletable())
                    .build();
        }).toList();
        return HiveRecordsVO.builder().files(list).total((int) pageResult.getTotalElements()).page(page).pageSize(pageSize).build();
    }

    @DeleteMapping("/files/{fileKey}")
    public ResponseEntity<Void> deleteFiles(@PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByFileKey(fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        HiveRecord hiveRecord = optional.get();
        hiveRecord.setStatus(HiveRecordStatus.TO_BE_DELETED);
        hiveRecord.setDeletable(false);
        hiveRecordRepository.save(hiveRecord);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/files/sync/{bucket}")
    public ResponseEntity<Void> syncFiles(@PathVariable("bucket") HiveRecordSource source) {
        List<HiveRecord> recordList = hiveRecordRepository.findByStatusAndDeletedIsFalse(HiveRecordStatus.DB_ONLY);
        for (HiveRecord record : recordList) {
            record.setDeleted(true);
            hiveRecordRepository.save(record);
        }
         recordList = hiveRecordRepository.findByStatusAndDeletedIsFalse(HiveRecordStatus.TO_BE_DELETED);
        for (HiveRecord record : recordList) {
            hiveOssService.using(record.getSource()).delete(HiveOssTask.createTask().withBucket(record.getSource()).withKey(record.getFileKey()));
            record.setDeleted(true);
            hiveRecordRepository.save(record);
        }
        hiveSyncService.syncLocalToRemote(source);
        return ResponseEntity.ok().build();
    }
}
