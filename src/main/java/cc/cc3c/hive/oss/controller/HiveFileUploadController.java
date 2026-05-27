package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.controller.vo.HiveUploadCheckRequest;
import cc.cc3c.hive.oss.controller.vo.HiveUploadCheckVO;
import cc.cc3c.hive.oss.controller.vo.HiveUploadVO;
import cc.cc3c.hive.oss.service.HiveUploadService;
import cc.cc3c.hive.oss.service.UploadAlreadyInProgressException;
import cc.cc3c.hive.oss.web.CategoryContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

@Slf4j
@RestController
public class HiveFileUploadController {

    private final HiveUploadService hiveUploadService;

    public HiveFileUploadController(HiveUploadService hiveUploadService) {
        this.hiveUploadService = hiveUploadService;
    }

    @PostMapping("/categories/{category}/files/upload/check")
    public ResponseEntity<?> checkUpload(CategoryContext category,
                                         @RequestBody HiveUploadCheckRequest request) {
        if (request == null || StringUtils.isBlank(request.getFileName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileName is required"));
        }
        HiveUploadCheckVO result = hiveUploadService.checkUpload(category.categoryId(), request.getFileName());
        if (HiveRecordStatus.UPLOADING.equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/categories/{category}/files/upload")
    public ResponseEntity<?> upload(CategoryContext category, HttpServletRequest request) throws IOException {
        try {
            JakartaServletFileUpload<?, ?> upload = new JakartaServletFileUpload<>();
            FileItemInputIterator iter = upload.getItemIterator(request);

            String fileKey = null;
            while (iter.hasNext()) {
                FileItemInput item = iter.next();
                if (item.isFormField() || fileKey != null) {
                    item.getInputStream().transferTo(OutputStream.nullOutputStream());
                    continue;
                }
                fileKey = hiveUploadService.uploadStreaming(category.categoryId(), category.storageClass(), category.uiVariant(), item.getName(), item.getInputStream());
            }

            if (fileKey == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "请上传一个文件"));
            }
            return ResponseEntity.ok(HiveUploadVO.builder().fileKey(fileKey).build());
        } catch (UploadAlreadyInProgressException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "上传失败"));
        }
    }

}
