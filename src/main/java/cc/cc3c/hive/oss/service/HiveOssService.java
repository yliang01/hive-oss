package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.oss.vendor.HiveOss;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class HiveOssService {
    @Autowired
    @Qualifier("alibabaOss")
    private HiveOss alibabaOss;

    @Autowired
    @Qualifier("tencentOss")
    private HiveOss tencentOss;

    public HiveOss using(HiveRecordSource source) {
        switch (source) {
            case ALIBABA_ACHIEVE, ALIBABA_STANDARD -> {
                return alibabaOss;
            }
        }
        return null;
    }
}
