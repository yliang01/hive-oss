package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.service.LocalDownloadGroupConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {HiveCategoryController.class, HiveGroupController.class, HiveFileGroupController.class})
public class CategoryApiExceptionHandler {

    @ExceptionHandler(LocalDownloadGroupConflictException.class)
    public ResponseEntity<Map<String, String>> handleLocalDownloadGroupConflict(LocalDownloadGroupConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
