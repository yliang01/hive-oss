package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 历史缩略图补全与加密一致性巡检的扩展点。
 * 设计见 docs/modules/thumbnail-backfill-and-audit.md。
 * <p>
 * 补全流程：查询 IMAGE_PREVIEW 下尚无 READY 缩略图的图片记录（无 image_meta 或 thumb_status 非 READY），下载原图（解密）→ 生成缩略图 → 加密上传 → 写入 hive_record_image_meta。
 * 巡检：listObjects 与 DB file_key/thumb_key 比对，报告未纳入管理的 key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailBackfillService {

    private final FileCategoryResolver fileCategoryResolver;
    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final ThumbnailGeneratorService thumbnailGeneratorService;

    /**
     * 对指定分类（如 IMAGE_PREVIEW）执行缩略图补全：为尚无 READY 缩略图的图片记录生成并加密上传缩略图。
     * 建议由管理端或定时任务调用；单条失败不阻断，仅记日志。
     *
     * @param categoryCode 分类 code，如 "IMAGE_PREVIEW"
     * @return 本批处理条数（实际实现时可返回成功/失败计数）
     */
    public int backfillThumbnailsForCategory(String categoryCode) {
        if (categoryCode == null || !"IMAGE_PREVIEW".equalsIgnoreCase(categoryCode.trim())) {
            log.warn("backfill only supported for IMAGE_PREVIEW, got: {}", categoryCode);
            return 0;
        }
        var category = fileCategoryResolver.resolveCategory(categoryCode);
        String bucketName = category.getBucketName();
        var records = hiveRecordRepository.findByBucketNameAndDeletedIsFalse(bucketName);
        var recordIds = records.stream().map(HiveRecord::getId).toList();
        Map<Integer, HiveRecordImageMeta> metaMap = imageMetaRepository.findByHiveRecordIdIn(recordIds).stream()
                .collect(Collectors.toMap(HiveRecordImageMeta::getHiveRecordId, m -> m));
        long needBackfill = records.stream()
                .filter(r -> thumbnailGeneratorService.isSupportedImage(r.getFileName()))
                .filter(r -> {
                    HiveRecordImageMeta m = metaMap.get(r.getId());
                    return m == null || "FAILED".equals(m.getThumbStatus()) || "PENDING".equals(m.getThumbStatus());
                })
                .count();
        // TODO: for each such record, download original (decrypt), generate thumb, upload with ThumbnailKeyHelper + withEncryption, update record. See docs/modules/thumbnail-backfill-and-audit.md.
        log.info("thumbnail backfill requested for category={}, bucket={}, records needing backfill={} (stub)", categoryCode, bucketName, needBackfill);
        return 0;
    }
}
