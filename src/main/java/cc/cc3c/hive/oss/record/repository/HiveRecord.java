package cc.cc3c.hive.oss.record.repository;

import cc.cc3c.hive.oss.record.HiveRecordSource;
import cc.cc3c.hive.oss.record.HiveRecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class HiveRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String fileName;
    private String fileKey;
    private Boolean zipped;
    @Enumerated(EnumType.STRING)
    private HiveRecordSource source;
    private Long size;
    private LocalDateTime updateTime;
    @Enumerated(EnumType.STRING)
    private HiveRecordStatus status;
    private LocalDateTime lastSyncTime;
    private Boolean deletable;
}
