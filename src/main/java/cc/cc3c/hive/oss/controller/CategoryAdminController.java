package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.CategoryUpsertRequest;
import cc.cc3c.hive.oss.controller.vo.FileCategoryVO;
import cc.cc3c.hive.oss.controller.vo.FileGroupVO;
import cc.cc3c.hive.oss.controller.vo.GroupUpsertRequest;
import cc.cc3c.hive.oss.service.FileGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category-admin")
public class CategoryAdminController {
    private final FileGroupService fileGroupService;

    public CategoryAdminController(FileGroupService fileGroupService) {
        this.fileGroupService = fileGroupService;
    }

    @GetMapping("/categories")
    public List<FileCategoryVO> categories() {
        return fileGroupService.listCategoriesForAdmin();
    }

    @PostMapping("/categories")
    public FileCategoryVO createCategory(@RequestBody CategoryUpsertRequest request) {
        return fileGroupService.createCategory(request);
    }

    @PutMapping("/categories/{categoryCode}")
    public FileCategoryVO updateCategory(@PathVariable("categoryCode") String categoryCode, @RequestBody CategoryUpsertRequest request) {
        return fileGroupService.updateCategory(categoryCode, request);
    }

    @PostMapping("/categories/{categoryCode}/disable")
    public ResponseEntity<Void> disableCategory(@PathVariable("categoryCode") String categoryCode) {
        fileGroupService.disableCategory(categoryCode);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/categories/{categoryCode}")
    public ResponseEntity<Void> deleteCategory(@PathVariable("categoryCode") String categoryCode) {
        fileGroupService.deleteCategory(categoryCode);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/categories/{categoryCode}/groups")
    public List<FileGroupVO> groups(@PathVariable("categoryCode") String categoryCode) {
        return fileGroupService.listGroupsForAdmin(categoryCode);
    }

    @PostMapping("/categories/{categoryCode}/groups")
    public FileGroupVO createGroup(@PathVariable("categoryCode") String categoryCode, @RequestBody GroupUpsertRequest request) {
        return fileGroupService.createGroup(categoryCode, request);
    }

    @PutMapping("/categories/{categoryCode}/groups/{groupId}")
    public FileGroupVO updateGroup(@PathVariable("categoryCode") String categoryCode,
                                   @PathVariable("groupId") Long groupId,
                                   @RequestBody GroupUpsertRequest request) {
        return fileGroupService.updateGroup(groupId, categoryCode, request);
    }

    @DeleteMapping("/categories/{categoryCode}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable("categoryCode") String categoryCode, @PathVariable("groupId") Long groupId) {
        fileGroupService.deleteGroup(groupId, categoryCode);
        return ResponseEntity.ok().build();
    }
}
