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
    public List<FileGroupVO> getGroups(@PathVariable("category") String category) {
        return fileGroupService.listGroups(category);
    }

    @PostMapping
    public FileGroupVO createGroup(@PathVariable("category") String category, @RequestBody GroupUpsertRequest request) {
        return fileGroupService.createGroup(category, request);
    }

    @PutMapping("/{groupCode}")
    public FileGroupVO updateGroup(@PathVariable("category") String category,
                                   @PathVariable("groupCode") String groupCode,
                                   @RequestBody GroupUpsertRequest request) {
        return fileGroupService.updateGroup(groupCode, category, request);
    }

    @DeleteMapping("/{groupCode}")
    public ResponseEntity<Void> deleteGroup(@PathVariable("category") String category, @PathVariable("groupCode") String groupCode) {
        fileGroupService.deleteGroup(groupCode, category);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{groupCode}/files:assign")
    public ResponseEntity<Void> assignFilesToGroup(@PathVariable("category") String category,
                                                   @PathVariable("groupCode") String groupCode,
                                                   @RequestBody FileGroupAssignRequest request) {
        fileGroupService.assignFiles(category, groupCode, request.getFileKeys(), request.getOperator());
        return ResponseEntity.ok().build();
    }
}
