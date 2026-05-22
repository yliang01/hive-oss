package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.FileCategoryVO;
import cc.cc3c.hive.oss.controller.vo.FileGroupAssignRequest;
import cc.cc3c.hive.oss.controller.vo.FileGroupVO;
import cc.cc3c.hive.oss.service.FileGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class HiveCategoryController {

    private final FileGroupService fileGroupService;

    public HiveCategoryController(FileGroupService fileGroupService) {
        this.fileGroupService = fileGroupService;
    }

    @GetMapping("/categories")
    public List<FileCategoryVO> getCategories() {
        return fileGroupService.listCategories();
    }

    @GetMapping("/categories/{category}/groups")
    public List<FileGroupVO> getGroups(@PathVariable("category") String category) {
        return fileGroupService.listGroups(category);
    }

    @PostMapping("/categories/{category}/groups/{groupId}/files:assign")
    public ResponseEntity<Void> assignFilesToGroup(@PathVariable("category") String category,
                                                   @PathVariable("groupId") Long groupId,
                                                   @RequestBody FileGroupAssignRequest request) {
        fileGroupService.assignFiles(category, groupId, request.getFileKeys(), request.getOperator());
        return ResponseEntity.ok().build();
    }
}
