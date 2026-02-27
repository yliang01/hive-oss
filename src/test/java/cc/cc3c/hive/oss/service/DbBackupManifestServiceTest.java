//package cc.cc3c.hive.oss.service;
//
//import org.junit.Test;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//public class DbBackupManifestServiceTest {
//
//    @Test
//    public void keyBuilders_buildExpectedKeys() {
//        DbBackupManifestService service = new DbBackupManifestService();
//        String batchId = "hive-20260227153000";
//
//        assertThat(service.manifestKey(batchId)).isEqualTo("db-backup/hive-20260227153000.sql.gz.manifest.json");
//        assertThat(service.archiveKey(batchId)).isEqualTo("db-backup/hive-20260227153000.sql.gz");
//        assertThat(service.checksumKey(batchId)).isEqualTo("db-backup/hive-20260227153000.sql.gz.sha256");
//    }
//
//    @Test
//    public void deriveBatchIdFromManifestKey_extractsBatchId() {
//        String batchId = DbBackupManifestService.deriveBatchIdFromManifestKey(
//                "db-backup/hive-20260227153000.sql.gz.manifest.json"
//        );
//        assertThat(batchId).isEqualTo("hive-20260227153000");
//    }
//}
