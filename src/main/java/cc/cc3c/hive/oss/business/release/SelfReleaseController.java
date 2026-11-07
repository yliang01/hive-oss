package cc.cc3c.hive.oss.business.release;

import cc.cc3c.hive.release.common.SecretException;
import cc.cc3c.hive.release.common.DownloadTicket;
import cc.cc3c.hive.release.publisher.ReleaseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/self-release")
public class SelfReleaseController {
    private final SelfReleaseService service;

    public SelfReleaseController(SelfReleaseService service) {
        this.service = service;
    }

    public static class Publish {
        public String version;
        public String password;
    }

    @GetMapping
    public SelfReleaseService.View status() {
        return service.view();
    }

    @PostMapping("/publish")
    public SelfReleaseService.View publish(@RequestBody Publish body) {
        if (body.version == null) throw new SecretException("请填写版本号。");
        char[] password = body.password == null ? new char[0] : body.password.toCharArray();
        body.password = null;
        try {
            return service.publish(body.version, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public record Ticket(String version, int seconds) { }
    @PostMapping(value = "/ticket", produces = "application/json")
    public ResponseEntity<DownloadTicket> ticket(@RequestBody Ticket body) throws Exception {
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=download.ticket.json")
                .body(service.ticket(body.version(), body.seconds()));
    }

    @ExceptionHandler({SecretException.class, ReleaseException.class})
    public ResponseEntity<Map<String, String>> rejected(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
