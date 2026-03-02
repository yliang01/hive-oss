//package cc.cc3c.hive.oss.service;
//
//import cc.cc3c.hive.domain.model.HiveStorageProvider;
//import cc.cc3c.hive.oss.controller.vo.DbBackupAckVO;
//import cc.cc3c.hive.oss.controller.vo.DbBackupListVO;
//import cc.cc3c.hive.oss.vendor.HiveOss;
//import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
//import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnitRunner;
//
//import java.util.Date;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.when;
//
//@RunWith(MockitoJUnitRunner.class)
//public class DbBackupQueryServiceTest {
//
//    @Mock
//    private HiveOssService hiveOssService;
//    @Mock
//    private DbBackupManifestService manifestService;
//    @Mock
//    private HiveOss oss;
//
//    private DbBackupQueryService service;
//
//    @Before
//    public void setUp() {
//        service = new DbBackupQueryService(hiveOssService, manifestService);
//        when(hiveOssService.using(HiveStorageProvider.ALIBABA)).thenReturn(oss);
//    }
//
//    @Test
//    public void getByBatchId_returnsUploadedWhenManifestAndArtifactsExist() throws Exception {
//        String batchId = "hive-20260227140000";
//        when(manifestService.manifestKey(batchId)).thenReturn("db-backup/hive-20260227140000.sql.gz.manifest.json");
//        when(manifestService.archiveKey(batchId)).thenReturn("db-backup/hive-20260227140000.sql.gz");
//        when(manifestService.checksumKey(batchId)).thenReturn("db-backup/hive-20260227140000.sql.gz.sha256");
//        when(oss.doesObjectExist(any(HiveOssTask.class))).thenReturn(true);
//        HiveOssObject archiveObj = new HiveOssObject();
//        archiveObj.setFileKey("db-backup/hive-20260227140000.sql.gz");
//        archiveObj.setLastModified(new Date());
//        when(oss.listObjects(any(HiveOssTask.class))).thenReturn(List.of(archiveObj));
//
//        Optional<DbBackupAckVO> result = service.getByBatchId(batchId);
//
//        assertThat(result).isPresent();
//        assertThat(result.get().getStatus()).isEqualTo("UPLOADED");
//        assertThat(result.get().getArchiveObjectKey()).isEqualTo("db-backup/hive-20260227140000.sql.gz");
//        assertThat(result.get().getManifestObjectKey()).isEqualTo("db-backup/hive-20260227140000.sql.gz.manifest.json");
//    }
//
//    @Test
//    public void getByBatchId_returnsPendingWhenChecksumObjectMissing() throws Exception {
//        String batchId = "hive-20260227140000";
//        when(manifestService.manifestKey(batchId)).thenReturn("db-backup/hive-20260227140000.sql.gz.manifest.json");
//        when(manifestService.archiveKey(batchId)).thenReturn("db-backup/hive-20260227140000.sql.gz");
//        when(manifestService.checksumKey(batchId)).thenReturn("db-backup/hive-20260227140000.sql.gz.sha256");
//        when(oss.doesObjectExist(any(HiveOssTask.class))).thenAnswer(invocation -> {
//            HiveOssTask task = invocation.getArgument(0, HiveOssTask.class);
//            return "db-backup/hive-20260227140000.sql.gz.manifest.json".equals(task.getKey())
//                    || "db-backup/hive-20260227140000.sql.gz".equals(task.getKey());
//        });
//
//        Optional<DbBackupAckVO> result = service.getByBatchId(batchId);
//
//        assertThat(result).isPresent();
//        assertThat(result.get().getStatus()).isEqualTo("PENDING");
//    }
//
//    @Test
//    public void listBackups_usesManifestKeyAndSkipsMissingChecksum() throws Exception {
//        HiveOssObject m1 = new HiveOssObject();
//        m1.setFileKey("db-backup/hive-20260227140000.sql.gz.manifest.json");
//        m1.setLastModified(new Date());
//        HiveOssObject a1 = new HiveOssObject();
//        a1.setFileKey("db-backup/hive-20260227140000.sql.gz");
//        HiveOssObject c1 = new HiveOssObject();
//        c1.setFileKey("db-backup/hive-20260227140000.sql.gz.sha256");
//
//        HiveOssObject m2 = new HiveOssObject();
//        m2.setFileKey("db-backup/hive-20260227150000.sql.gz.manifest.json");
//        m2.setLastModified(new Date());
//        HiveOssObject a2 = new HiveOssObject();
//        a2.setFileKey("db-backup/hive-20260227150000.sql.gz");
//
//        when(oss.listObjects(any(HiveOssTask.class))).thenReturn(List.of(m1, a1, c1, m2, a2));
//        when(manifestService.listManifestObjects(any())).thenCallRealMethod();
//        when(manifestService.archiveKey("hive-20260227140000")).thenReturn("db-backup/hive-20260227140000.sql.gz");
//        when(manifestService.checksumKey("hive-20260227140000")).thenReturn("db-backup/hive-20260227140000.sql.gz.sha256");
//        when(manifestService.archiveKey("hive-20260227150000")).thenReturn("db-backup/hive-20260227150000.sql.gz");
//        when(manifestService.checksumKey("hive-20260227150000")).thenReturn("db-backup/hive-20260227150000.sql.gz.sha256");
//
//        DbBackupListVO result = service.listBackups();
//
//        assertThat(result.getTotal()).isEqualTo(1);
//        assertThat(result.getItems()).hasSize(1);
//        assertThat(result.getItems().get(0).getBatchId()).isEqualTo("hive-20260227140000");
//    }
//}
