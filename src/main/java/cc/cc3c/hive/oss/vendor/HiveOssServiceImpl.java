package cc.cc3c.hive.oss.vendor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class HiveOssServiceImpl implements HiveOssService {

    @Autowired
    @Qualifier("alibabaOss")
    private HiveOss alibabaOss;

    @Autowired
    @Qualifier("tencentOss")
    private HiveOss tencentOss;

    @Override
    public HiveOss alibabaOss() {
        return alibabaOss;
    }

    @Override
    public HiveOss tencentOss() {
        return tencentOss;
    }
}
