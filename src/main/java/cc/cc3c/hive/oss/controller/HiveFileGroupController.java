package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.FileGroupAssignRequest;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.web.CategoryContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HiveFileGroupController {
    private final FileGroupService fileGroupService;

    public HiveFileGroupController(FileGroupService fileGroupService) {
        this.fileGroupService = fileGroupService;
    }

    @PostMapping("/categories/{category}/files:assign-group")
    public ResponseEntity<Void> assignFiles(CategoryContext category, @RequestBody FileGroupAssignRequest request) {
        fileGroupService.assignFiles(category.categoryId(), category.code(), request.getGroupCode(), request.getFileKeys());
        return ResponseEntity.ok().build();
    }
}
