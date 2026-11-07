package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class FileSortRuleTest {

    @Test
    void parsesOrderedRulesAndRejectsDuplicates() {
        List<FileSortRule> rules = FileSortRule.parse(List.of("updateTime:desc", "fileName:asc", "size:desc"));

        assertThat(rules).containsExactly(
                new FileSortRule(FileSortField.UPDATE_TIME, Sort.Direction.DESC),
                new FileSortRule(FileSortField.FILE_NAME, Sort.Direction.ASC),
                new FileSortRule(FileSortField.SIZE, Sort.Direction.DESC));
        assertThatThrownBy(() -> FileSortRule.parse(List.of("size:asc", "size:desc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> FileSortRule.parse(List.of("group:asc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void groupsFilesBeforeApplyingUserSortRules() {
        HiveRecordRepository repository = mock(HiveRecordRepository.class);
        HiveRecordQueryService service = new HiveRecordQueryService(repository);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        FileQueryParams params = new FileQueryParams(null, false, null, 0, 50,
                FileSortRule.parse(List.of("updateTime:asc")));

        service.query(1L, params);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        List<Sort.Order> orders = pageable.getValue().getSort().stream().toList();
        assertThat(orders).extracting(Sort.Order::getProperty)
                .containsExactly("group.sortOrder", "group.id", "updateTime", "id");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(orders.get(0).getNullHandling()).isEqualTo(Sort.NullHandling.NATIVE);
        assertThat(orders.get(1).getNullHandling()).isEqualTo(Sort.NullHandling.NATIVE);
        assertThat(orders.get(3).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void groupsFilesEvenWithoutUserSortRules() {
        HiveRecordRepository repository = mock(HiveRecordRepository.class);
        HiveRecordQueryService service = new HiveRecordQueryService(repository);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.query(1L, new FileQueryParams(null, false, null, 0, 50, List.of()));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().stream().toList())
                .containsExactly(
                        Sort.Order.asc("group.sortOrder"),
                        Sort.Order.asc("group.id"),
                        Sort.Order.desc("id"));
    }
}
