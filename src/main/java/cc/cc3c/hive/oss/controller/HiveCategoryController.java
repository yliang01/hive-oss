package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.CategoryUpsertRequest;
import cc.cc3c.hive.oss.controller.vo.FileCategoryVO;
import cc.cc3c.hive.oss.service.FileCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class HiveCategoryController {

    private final FileCategoryService fileCategoryService;

    public HiveCategoryController(FileCategoryService fileCategoryService) {
        this.fileCategoryService = fileCategoryService;
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

    @DeleteMapping("/{categoryCode}")
    public ResponseEntity<Void> deleteCategory(@PathVariable("categoryCode") String categoryCode) {
        fileCategoryService.deleteCategory(categoryCode);
        return ResponseEntity.ok().build();
    }
}
