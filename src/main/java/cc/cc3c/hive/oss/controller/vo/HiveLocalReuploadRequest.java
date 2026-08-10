package cc.cc3c.hive.oss.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HiveLocalReuploadRequest {
    private String fileKey;
    private LocalDateTime localCheckTime;
    private Long observedSize;
}
