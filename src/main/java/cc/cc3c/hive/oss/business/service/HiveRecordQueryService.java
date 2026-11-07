package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.FileGroup;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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
        Pageable pageable = PageRequest.of(params.page(), params.pageSize(), buildSort(params));
        return hiveRecordRepository.findAll(buildSpec(categoryId, params), pageable);
    }

    private Sort buildSort(FileQueryParams params) {
        List<Sort.Order> orders = new ArrayList<>(List.of(
                Sort.Order.asc("group.sortOrder"),
                Sort.Order.asc("group.id")));
        for (FileSortRule rule : params.sortRules()) {
            orders.add(rule.field().toOrder(rule.direction()));
        }
        orders.add(Sort.Order.desc("id"));
        return Sort.by(orders);
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

            if (StringUtils.isNotBlank(params.groupCode())) {
                Join<HiveRecord, FileGroup> group = root.join("group", JoinType.INNER);
                predicates.add(cb.equal(group.get("category").get("id"), categoryId));
                predicates.add(cb.equal(cb.lower(group.get("groupCode")), params.groupCode().toLowerCase()));
            } else if (params.ungroupedOnly()) {
                Join<HiveRecord, FileGroup> group = root.join("group", JoinType.LEFT);
                predicates.add(cb.isNull(group.get("id")));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
