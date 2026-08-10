package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.HiveLocalReuploadRequest;
import cc.cc3c.hive.oss.controller.vo.HiveLocalReuploadStatusVO;
import cc.cc3c.hive.oss.service.HiveLocalReuploadService;
import cc.cc3c.hive.oss.service.LocalReuploadConflictException;
import cc.cc3c.hive.oss.web.CategoryContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
public class HiveFileLocalReuploadController {

    private final HiveLocalReuploadService reuploadService;

    public HiveFileLocalReuploadController(HiveLocalReuploadService reuploadService) {
        this.reuploadService = reuploadService;
    }

    @PostMapping("/categories/{category}/files:reupload-local")
    public ResponseEntity<?> reuploadLocal(CategoryContext category, @RequestBody HiveLocalReuploadRequest request) {
        try {
            HiveLocalReuploadStatusVO result = reuploadService.reuploadLocal(category, request);
            return ResponseEntity.accepted().body(result);
        } catch (LocalReuploadConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("start local reupload failed {}", category.code(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage() == null ? "重新上传失败" : e.getMessage()));
        }
    }

    @GetMapping("/categories/{category}/files/local-reupload-tasks/{taskId}")
    public ResponseEntity<HiveLocalReuploadStatusVO> getReuploadStatus(CategoryContext category,
                                                                       @PathVariable("taskId") String taskId) {
        HiveLocalReuploadStatusVO result = reuploadService.getStatus(taskId);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }
}
