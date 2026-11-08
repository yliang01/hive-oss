package cc.cc3c.hive.oss.setup;

import cc.cc3c.hive.release.common.SecretException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/setup")
public class SetupController {
    private final SetupService service;

    public SetupController(SetupService service) {
        this.service = service;
    }

    public record ManifestImport(String manifest) {
    }

    public record Import(String content) {
    }

    public static class Unlock {
        public String password;
    }

    @GetMapping
    public SetupService.Status status() {
        return service.status();
    }

    @PostMapping("/download")
    public SetupService.Status download(@RequestBody ManifestImport body) throws Exception {
        return service.downloadManifest(body.manifest());
    }

    @PostMapping("/yaml")
    public SetupService.Status yaml(@RequestBody Import body) throws Exception {
        return service.loadYaml(Base64.getDecoder().decode(body.content()));
    }

    @PostMapping("/unlock")
    public SetupService.Status unlock(@RequestBody Unlock body) throws Exception {
        char[] password = body.password == null ? new char[0] : body.password.toCharArray();
        body.password = null;
        try {
            return service.unlock(password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> failure(Exception exception) {
        String message = exception instanceof SecretException ? exception.getMessage() : "操作失败，请检查文件和网络；已有配置保留。";
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
