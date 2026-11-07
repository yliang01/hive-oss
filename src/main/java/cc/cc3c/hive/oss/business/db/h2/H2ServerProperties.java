package cc.cc3c.hive.oss.business.db.h2;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("hive.h2.server")
public class H2ServerProperties {
    private Boolean enabled;
    private Integer port;
    private String baseDir;
    private Boolean allowOthers;
}
