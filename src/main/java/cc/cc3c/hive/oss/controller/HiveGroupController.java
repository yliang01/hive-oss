package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.FileGroupAssignRequest;
import cc.cc3c.hive.oss.controller.vo.FileGroupVO;
import cc.cc3c.hive.oss.controller.vo.GroupUpsertRequest;
import cc.cc3c.hive.oss.service.FileGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories/{category}/groups")
public class HiveGroupController {

    private final FileGroupService fileGroupService;

    public HiveGroupController(FileGroupService fileGroupService) {
        this.fileGroupService = fileGroupService;
    }

    @GetMapping
    public List<FileGroupVO> getGroups(@PathVariable("category") String category,
                                       @RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
        return fileGroupService.listGroups(category, includeDisabled);
    }

    @PostMapping
    public FileGroupVO createGroup(@PathVariable("category") String category, @RequestBody GroupUpsertRequest request) {
        return fileGroupService.createGroup(category, request);
    }

    @PutMapping("/{groupId}")
    public FileGroupVO updateGroup(@PathVariable("category") String category,
                                   @PathVariable("groupId") Long groupId,
                                   @RequestBody GroupUpsertRequest request) {
        return fileGroupService.updateGroup(groupId, category, request);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable("category") String category, @PathVariable("groupId") Long groupId) {
        fileGroupService.deleteGroup(groupId, category);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{groupId}/files:assign")
    public ResponseEntity<Void> assignFilesToGroup(@PathVariable("category") String category,
                                                   @PathVariable("groupId") Long groupId,
                                                   @RequestBody FileGroupAssignRequest request) {
        fileGroupService.assignFiles(category, groupId, request.getFileKeys(), request.getOperator());
        return ResponseEntity.ok().build();
    }
}
