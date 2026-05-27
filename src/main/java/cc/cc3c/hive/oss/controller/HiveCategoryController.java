package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.CategoryUpsertRequest;
import cc.cc3c.hive.oss.controller.vo.FileCategoryVO;
import cc.cc3c.hive.oss.service.FileCategoryService;
import cc.cc3c.hive.oss.service.FileGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class HiveCategoryController {

    private final FileCategoryService fileCategoryService;
    private final FileGroupService fileGroupService;

    public HiveCategoryController(FileCategoryService fileCategoryService, FileGroupService fileGroupService) {
        this.fileCategoryService = fileCategoryService;
        this.fileGroupService = fileGroupService;
    }

    @GetMapping
    public List<FileCategoryVO> getCategories(@RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
        return fileCategoryService.listCategories(includeDisabled);
    }

    @PostMapping
    public FileCategoryVO createCategory(@RequestBody CategoryUpsertRequest request) {
        return fileCategoryService.createCategory(request);
    }

    @PutMapping
    public FileCategoryVO updateCategory(@RequestBody CategoryUpsertRequest request) {
        return fileCategoryService.updateCategory(request);
    }

    @PostMapping("/{categoryCode}/disable")
    public ResponseEntity<Void> disableCategory(@PathVariable("categoryCode") String categoryCode) {
        fileGroupService.disableCategory(categoryCode);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{categoryCode}")
    public ResponseEntity<Void> deleteCategory(@PathVariable("categoryCode") String categoryCode) {
        fileGroupService.deleteCategory(categoryCode);
        return ResponseEntity.ok().build();
    }
}
