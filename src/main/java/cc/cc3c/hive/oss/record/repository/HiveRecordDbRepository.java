package cc.cc3c.hive.oss.record.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HiveRecordDbRepository extends JpaRepository<HiveRecord, Integer> {

    Optional<HiveRecord> findByFileKey(String fileKey);
}
