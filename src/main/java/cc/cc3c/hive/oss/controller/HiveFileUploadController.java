package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.controller.vo.HiveUploadCheckRequest;
import cc.cc3c.hive.oss.controller.vo.HiveUploadCheckVO;
import cc.cc3c.hive.oss.controller.vo.HiveUploadVO;
import cc.cc3c.hive.oss.service.FileCategoryResolver;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.service.HiveUploadService;
import cc.cc3c.hive.oss.service.UploadAlreadyInProgressException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
public class HiveFileUploadController {

    private final FileGroupService fileGroupService;
    private final FileCategoryResolver fileCategoryResolver;
    private final HiveUploadService hiveUploadService;

    public HiveFileUploadController(FileGroupService fileGroupService,
                                    FileCategoryResolver fileCategoryResolver,
                                    HiveUploadService hiveUploadService) {
        this.fileGroupService = fileGroupService;
        this.fileCategoryResolver = fileCategoryResolver;
        this.hiveUploadService = hiveUploadService;
    }

    @PostMapping("/categories/{category}/files/upload")
    public ResponseEntity<?> upload(@PathVariable("category") String category, HttpServletRequest request) throws IOException {
        String bucketName = fileGroupService.resolveBucket(category);
        CategoryStorageClass storageClass = fileCategoryResolver.resolveStorageClass(category);
        JakartaServletFileUpload<?, ?> upload = new JakartaServletFileUpload<>();
        FileItemInputIterator iter = upload.getItemIterator(request);
        if (!iter.hasNext()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传一个文件"));
        }
        FileItemInput item = iter.next();
        if (item.isFormField()) {
            return ResponseEntity.badRequest().body(Map.of("error", "需要文件类型 part，不能是表单字段"));
        }
        try {
            var cat = fileCategoryResolver.resolveCategory(category);
            String fileKey = hiveUploadService.uploadStreaming(bucketName, storageClass, cat.getUiVariant(), item.getName(), item.getInputStream());
            return ResponseEntity.ok(HiveUploadVO.builder().fileKey(fileKey).build());
        } catch (UploadAlreadyInProgressException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "上传失败"));
        }
    }

    @PostMapping("/categories/{category}/files/upload/check")
    public ResponseEntity<?> checkUpload(@PathVariable("category") String category,
                                         @RequestBody HiveUploadCheckRequest request) {
        if (request == null || StringUtils.isBlank(request.getFileName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileName is required"));
        }
        String bucketName = fileGroupService.resolveBucket(category);
        HiveUploadCheckVO result = hiveUploadService.checkUpload(bucketName, request.getFileName());
        if (HiveRecordStatus.UPLOADING.equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
