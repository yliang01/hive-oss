package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class HiveUploadService implements FileAlterationListener {

    private static final String LEGACY_ARCHIVE_FOLDER_NAME = "ALIBABA_ACHIEVE";
    private final HiveRecordRepository hiveRecordRepository;

    private final HiveOssService hiveOssService;
    private final FileCategoryResolver fileCategoryResolver;

    public HiveUploadService(HiveRecordRepository hiveRecordRepository,
                             HiveOssService hiveOssService,
                             FileCategoryResolver fileCategoryResolver) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveOssService = hiveOssService;
        this.fileCategoryResolver = fileCategoryResolver;
    }

    @Getter
    private File legacyStandardFolder;
    @Getter
    private File legacyArchiveFolder;

    private String uploadDir;

    @Value("${hive.uploadDir}")
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @PostConstruct
    public void init() {
        // Legacy compatibility: watcher still consumes the historical folder names.
        legacyStandardFolder = new File(uploadDir + "ALIBABA_STANDARD");
        legacyArchiveFolder = new File(uploadDir + LEGACY_ARCHIVE_FOLDER_NAME);
        try {
            FileUtils.forceMkdir(legacyStandardFolder);
            FileUtils.forceMkdir(legacyArchiveFolder);
        } catch (IOException e) {
            log.error("fail to create upload folder", e);
            System.exit(1);
        }
    }

    @Override
    public void onStart(FileAlterationObserver fileAlterationObserver) {

    }

    @Override
    public void onDirectoryCreate(File file) {
    }

    @Override
    public void onDirectoryChange(File file) {

    }

    @Override
    public void onDirectoryDelete(File file) {

    }

    @Override
    public void onFileCreate(File file) {
        try {
            String fileName = file.getName();
            String fileKey = DigestUtils.md5Hex(file.getName());
            CategoryStorageClass storageClass = file.getCanonicalPath().contains(LEGACY_ARCHIVE_FOLDER_NAME)
                    ? CategoryStorageClass.ARCHIVE
                    : CategoryStorageClass.STANDARD;
            log.warn("legacy watcher folder semantics in use; path={}, inferredStorageClass={}", file.getCanonicalPath(), storageClass);
            String bucketName = fileCategoryResolver.resolveDefaultCategoryByStorageClass(storageClass).getBucketName();
            HiveOssTask task = HiveOssTask.createTask()
                    .withBucket(bucketName)
                    .withKey(fileKey)
                    .withInputStream(new FileInputStream(file))
                    .withStorageClass(storageClass.name())
                    .withEncryption(fileName);
            HiveRecord hiveRecord = new HiveRecord();
            hiveRecord.setFileName(fileName);
            hiveRecord.setFileKey(fileKey);
            hiveRecord.setZipped(false);
            hiveRecord.setProvider(HiveStorageProvider.ALIBABA);
            hiveRecord.setBucketName(task.getBucket());
            hiveRecord.setStorageClassCache(storageClass);
            hiveRecord.setStatus(HiveRecordStatus.UPLOADING);
            hiveRecordRepository.save(hiveRecord);

            hiveOssService.using(hiveRecord.getProvider()).upload(task);

            hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
            hiveRecordRepository.save(hiveRecord);
            FileUtils.deleteQuietly(file);
        } catch (Exception e) {
            log.error("onFileCreate", e);
        }
    }

    @Override
    public void onFileChange(File file) {

    }

    @Override
    public void onFileDelete(File file) {

    }

    @Override
    public void onStop(FileAlterationObserver fileAlterationObserver) {

    }

    public String uploadSync(String bucketName, CategoryStorageClass storageClass, String fileName, InputStream inputStream) throws Exception {
        String fileKey = DigestUtils.md5Hex(fileName);
        HiveOssTask task = HiveOssTask.createTask()
                .withBucket(bucketName)
                .withKey(fileKey)
                .withInputStream(inputStream)
                .withStorageClass(storageClass == null ? CategoryStorageClass.STANDARD.name() : storageClass.name())
                .withEncryption(fileName);
        HiveRecord hiveRecord = new HiveRecord();
        hiveRecord.setFileName(fileName);
        hiveRecord.setFileKey(fileKey);
        hiveRecord.setZipped(false);
        hiveRecord.setProvider(HiveStorageProvider.ALIBABA);
        hiveRecord.setBucketName(bucketName);
        hiveRecord.setStorageClassCache(storageClass == null ? CategoryStorageClass.STANDARD : storageClass);
        hiveRecord.setStatus(HiveRecordStatus.UPLOADING);
        hiveRecordRepository.save(hiveRecord);

        hiveOssService.using(hiveRecord.getProvider()).uploadSync(task);

        hiveRecord.setStatus(HiveRecordStatus.UPLOADED);
        hiveRecordRepository.save(hiveRecord);
        return fileKey;
    }
}
