package cc.cc3c.hive.oss.service;

import org.flywaydb.core.Flyway;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayLocalCheckMigrationTest {

    @Test
    void dropsPersistedLocalCheckStateButKeepsTheOriginalSizeBaseline() throws Exception {
        String url = "jdbc:h2:mem:local_check_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnExists(connection, "hive_record", "original_size")).isTrue();
            assertThat(columnExists(connection, "hive_record", "local_check_status")).isFalse();
            assertThat(columnExists(connection, "hive_record", "local_check_time")).isFalse();
            assertThat(columnExists(connection, "hive_record", "local_check_observed_size")).isFalse();
            assertThat(columnExists(connection, "hive_record_image_meta", "image_width")).isFalse();
            assertThat(columnExists(connection, "hive_record_image_meta", "image_height")).isFalse();
            assertThat(columnExists(connection, "file_category", "last_local_check_time")).isFalse();
            assertThat(columnExists(connection, "file_category", "last_local_scan_time")).isFalse();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
