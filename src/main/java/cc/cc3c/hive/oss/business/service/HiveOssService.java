package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.oss.business.vendor.HiveOss;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class HiveOssService {
    @Autowired
    @Qualifier("alibabaOss")
    private HiveOss alibabaOss;

    @Autowired(required = false)
    @Qualifier("tencentOss")
    private HiveOss tencentOss;

    public HiveOss using(HiveStorageProvider provider) {
        if (provider == null) {
            return alibabaOss;
        }
        switch (provider) {
            case ALIBABA -> {
                return alibabaOss;
            }
            case TENCENT -> {
                if (tencentOss == null) throw new IllegalStateException("Tencent OSS is disabled; configure and enable hive.oss.tencent.enabled.");
                return tencentOss;
            }
            default -> throw new IllegalArgumentException("unsupported provider: " + provider);
        }
    }
}
