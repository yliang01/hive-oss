package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileGroupRecord;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HiveRecordQueryService {
    private final HiveRecordRepository hiveRecordRepository;

    public HiveRecordQueryService(HiveRecordRepository hiveRecordRepository) {
        this.hiveRecordRepository = hiveRecordRepository;
    }

    public Page<HiveRecord> query(Long categoryId, FileQueryParams params) {
        Pageable pageable = PageRequest.of(params.page(), params.pageSize(), Sort.by("id").descending());
        return hiveRecordRepository.findAll(buildSpec(categoryId, params), pageable);
    }

    private Specification<HiveRecord> buildSpec(Long categoryId, FileQueryParams params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            predicates.add(cb.isFalse(root.get("deleted")));

            String keyword = StringUtils.trimToNull(params.keyword());
            if (keyword != null) {
                predicates.add(cb.like(cb.lower(root.get("fileName")), "%" + keyword.toLowerCase() + "%"));
            }

            if (params.groupId() != null) {
                predicates.add(cb.exists(groupRecordSubquery(root.get("id"), categoryId, params.groupId(), query.subquery(Long.class), cb)));
            } else if (params.ungroupedOnly()) {
                predicates.add(cb.not(cb.exists(groupRecordSubquery(root.get("id"), categoryId, null, query.subquery(Long.class), cb))));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Subquery<Long> groupRecordSubquery(jakarta.persistence.criteria.Path<Integer> recordId,
                                               Long categoryId,
                                               Long groupId,
                                               Subquery<Long> subquery,
                                               jakarta.persistence.criteria.CriteriaBuilder cb) {
        var groupRecord = subquery.from(FileGroupRecord.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(groupRecord.get("hiveRecord").get("id"), recordId));
        predicates.add(cb.equal(groupRecord.get("group").get("category").get("id"), categoryId));
        if (groupId != null) {
            predicates.add(cb.equal(groupRecord.get("group").get("id"), groupId));
        }
        return subquery.select(groupRecord.get("id")).where(predicates.toArray(Predicate[]::new));
    }
}
