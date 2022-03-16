package cc.cc3c.hive.repository;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Record {
    private int id;
    private String fileName;
    private String key;
    private boolean zipped;
    private String source;
    private long size;
    private String update_time;
}
