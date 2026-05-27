package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.HiveSyncVO;
import cc.cc3c.hive.oss.service.HiveSyncService;
import cc.cc3c.hive.oss.web.CategoryContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HiveFileSyncController {

    private final HiveSyncService hiveSyncService;

    public HiveFileSyncController(HiveSyncService hiveSyncService) {
        this.hiveSyncService = hiveSyncService;
    }

    @PostMapping("/categories/{category}/files/sync-remote")
    public ResponseEntity<HiveSyncVO> syncRemote(CategoryContext category) {
        try {
            HiveSyncVO hiveSyncVO = hiveSyncService.syncRemote(category.categoryId(), category.bucketName(), category.storageClass());
            return ResponseEntity.ok(hiveSyncVO);
        } catch (Exception e) {
            log.error("sync remote failed {}", category.requestedCode(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
