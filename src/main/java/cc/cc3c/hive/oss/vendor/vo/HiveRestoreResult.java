package cc.cc3c.hive.oss.vendor.vo;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class HiveRestoreResult {
    private HiveRestoreStatus restoreStatus;
    private ZonedDateTime expiryDate;
}
