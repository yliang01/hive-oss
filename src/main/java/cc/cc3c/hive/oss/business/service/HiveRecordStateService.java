package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.model.HiveStorageProvider;
import cc.cc3c.hive.oss.business.vendor.client.vo.HiveOssObject;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class HiveRecordStateService {

    private enum Event {
        START_UPLOAD,
        UPLOAD_SUCCEEDED,
        UPLOAD_FAILED,
        MARK_DELETE,
        CONFIRM_DELETE,
        CLEANUP_LOCAL,
        DISCOVER_REMOTE_OBJECT
    }

    private enum State {
        NEW,
        UPLOADING,
        UPLOADED,
        FAILED,
        OSS_ONLY,
        DB_ONLY,
        TO_BE_DELETED
    }

    public enum SyncOutcome {
        MATCHED,
        OSS_ONLY,
        OSS_TO_DB_MISMATCHED,
        DB_TO_OSS_MISSING,
        SKIPPED
    }

    private enum SyncEffect {
        NONE,
        REFRESH_REMOTE_METADATA,
        REFRESH_REMOTE_METADATA_NOT_DELETABLE,
        TOUCH_MISSING_NOT_DELETABLE
    }

    private record Transition(Set<State> from, State to) {
    }

    private record SyncTransition(State to, SyncEffect effect, SyncOutcome outcome) {
    }

    private static final Map<Event, Transition> TRANSITIONS = buildTransitions();
    private static final Map<State, SyncTransition> REMOTE_FOUND_TRANSITIONS = buildRemoteFoundTransitions();
    private static final Map<State, SyncTransition> REMOTE_MISSING_TRANSITIONS = buildRemoteMissingTransitions();

    private static Map<Event, Transition> buildTransitions() {
        Map<Event, Transition> transitions = new EnumMap<>(Event.class);
        transitions.put(Event.START_UPLOAD, new Transition(EnumSet.of(State.NEW, State.FAILED), State.UPLOADING));
        transitions.put(Event.UPLOAD_SUCCEEDED, new Transition(EnumSet.of(State.UPLOADING), State.UPLOADED));
        transitions.put(Event.UPLOAD_FAILED, new Transition(EnumSet.of(State.UPLOADING), State.FAILED));
        transitions.put(Event.MARK_DELETE, new Transition(EnumSet.of(State.UPLOADED), State.TO_BE_DELETED));
        transitions.put(Event.CONFIRM_DELETE, new Transition(EnumSet.of(State.TO_BE_DELETED), State.TO_BE_DELETED));
        transitions.put(Event.CLEANUP_LOCAL, new Transition(EnumSet.of(State.DB_ONLY), State.DB_ONLY));
        transitions.put(Event.DISCOVER_REMOTE_OBJECT, new Transition(EnumSet.of(State.NEW), State.OSS_ONLY));
        return Map.copyOf(transitions);
    }

    private static Map<State, SyncTransition> buildRemoteFoundTransitions() {
        return Map.ofEntries(
                syncTransitionEntry(State.UPLOADED, State.UPLOADED, SyncEffect.REFRESH_REMOTE_METADATA, SyncOutcome.MATCHED),
                syncTransitionEntry(State.DB_ONLY, State.UPLOADED, SyncEffect.REFRESH_REMOTE_METADATA, SyncOutcome.MATCHED),
                syncTransitionEntry(State.OSS_ONLY, State.OSS_ONLY, SyncEffect.REFRESH_REMOTE_METADATA_NOT_DELETABLE, SyncOutcome.OSS_ONLY),
                syncTransitionEntry(State.UPLOADING, State.UPLOADING, SyncEffect.NONE, SyncOutcome.SKIPPED),
                syncTransitionEntry(State.FAILED, State.FAILED, SyncEffect.NONE, SyncOutcome.OSS_TO_DB_MISMATCHED),
                syncTransitionEntry(State.TO_BE_DELETED, State.TO_BE_DELETED, SyncEffect.NONE, SyncOutcome.SKIPPED)
        );
    }

    private static Map<State, SyncTransition> buildRemoteMissingTransitions() {
        return Map.ofEntries(
                syncTransitionEntry(State.UPLOADED, State.DB_ONLY, SyncEffect.TOUCH_MISSING_NOT_DELETABLE, SyncOutcome.DB_TO_OSS_MISSING),
                syncTransitionEntry(State.DB_ONLY, State.DB_ONLY, SyncEffect.TOUCH_MISSING_NOT_DELETABLE, SyncOutcome.DB_TO_OSS_MISSING),
                syncTransitionEntry(State.OSS_ONLY, State.DB_ONLY, SyncEffect.TOUCH_MISSING_NOT_DELETABLE, SyncOutcome.DB_TO_OSS_MISSING),
                syncTransitionEntry(State.UPLOADING, State.UPLOADING, SyncEffect.NONE, SyncOutcome.SKIPPED),
                syncTransitionEntry(State.FAILED, State.FAILED, SyncEffect.NONE, SyncOutcome.SKIPPED),
                syncTransitionEntry(State.TO_BE_DELETED, State.TO_BE_DELETED, SyncEffect.NONE, SyncOutcome.SKIPPED)
        );
    }

    private static Map.Entry<State, SyncTransition> syncTransitionEntry(State from, State to, SyncEffect effect, SyncOutcome outcome) {
        return Map.entry(from, new SyncTransition(to, effect, outcome));
    }

    private void transition(HiveRecord record, Event event) {
        Transition transition = TRANSITIONS.get(event);
        if (transition == null) {
            throw new HiveRecordStateTransitionException("未知状态事件: " + event);
        }
        State current = currentState(record);
        if (!transition.from().contains(current)) {
            throw new HiveRecordStateTransitionException("非法文件状态流转: " + current + " --" + event + "--> " + transition.to());
        }
        if (transition.to() != null) {
            record.setStatus(toRecordStatus(transition.to()));
        }
    }

    private State currentState(HiveRecord record) {
        return record.getStatus() == null ? State.NEW : State.valueOf(record.getStatus().name());
    }

    private HiveRecordStatus toRecordStatus(State state) {
        if (State.NEW.equals(state)) {
            throw new HiveRecordStateTransitionException("NEW 不能作为持久化状态");
        }
        return HiveRecordStatus.valueOf(state.name());
    }

    public HiveRecord startUpload(HiveRecord record, FileCategoryEntity category, String fileName, String fileKey) {
        record.setFileName(fileName);
        record.setFileKey(fileKey);
        record.setZipped(false);
        record.setProvider(HiveStorageProvider.ALIBABA);
        record.setCategory(category);
        transition(record, Event.START_UPLOAD);
        record.setDeleted(false);
        record.setDeletable(false);
        record.setDownloadStatus(null);
        return record;
    }

    public void markUploaded(HiveRecord record, CategoryStorageClass storageClass) {
        transition(record, Event.UPLOAD_SUCCEEDED);
        record.setDeletable(storageClass != CategoryStorageClass.ARCHIVE);
    }

    public void markUploaded(HiveRecord record, FileCategoryEntity category, HiveOssObject object,
                              CategoryStorageClass storageClass) {
        transition(record, Event.UPLOAD_SUCCEEDED);
        refreshRemoteMetadata(record, category, object);
        record.setDeletable(isRemoteDeletable(record, storageClass));
    }

    public void refreshReuploaded(HiveRecord record, FileCategoryEntity category, HiveOssObject object,
                                  CategoryStorageClass storageClass) {
        record.setStatus(HiveRecordStatus.UPLOADED);
        refreshRemoteMetadata(record, category, object);
        record.setDeletable(isRemoteDeletable(record, storageClass));
    }

    public void markUploadFailed(HiveRecord record) {
        transition(record, Event.UPLOAD_FAILED);
        record.setDeletable(false);
    }

    public void markDelete(HiveRecord record) {
        if (!Boolean.TRUE.equals(record.getDeletable())) {
            throw new HiveRecordStateTransitionException("文件当前不允许删除");
        }
        transition(record, Event.MARK_DELETE);
        record.setDeletable(false);
    }

    public void confirmDeleted(HiveRecord record) {
        transition(record, Event.CONFIRM_DELETE);
        record.setCategory(null);
        record.setDeleted(true);
        record.setDeletable(false);
        record.setDownloadStatus(null);
    }

    public void cleanupLocalRecord(HiveRecord record) {
        transition(record, Event.CLEANUP_LOCAL);
        record.setCategory(null);
        record.setDeleted(true);
        record.setDeletable(false);
        record.setDownloadStatus(null);
    }

    public SyncOutcome applyRemoteFound(HiveRecord record, FileCategoryEntity category, HiveOssObject object,
                                        CategoryStorageClass storageClass) {
        SyncTransition transition = syncTransition(REMOTE_FOUND_TRANSITIONS, record, "远端存在");
        record.setStatus(toRecordStatus(transition.to()));
        applySyncEffect(record, transition.effect(), category, object, storageClass);
        return transition.outcome();
    }

    public SyncOutcome applyRemoteMissing(HiveRecord record) {
        SyncTransition transition = syncTransition(REMOTE_MISSING_TRANSITIONS, record, "远端缺失");
        record.setStatus(toRecordStatus(transition.to()));
        applySyncEffect(record, transition.effect(), null, null, null);
        return transition.outcome();
    }

    private SyncTransition syncTransition(Map<State, SyncTransition> transitions, HiveRecord record, String observationName) {
        State state = currentState(record);
        SyncTransition transition = transitions.get(state);
        if (transition == null) {
            throw new HiveRecordStateTransitionException("非法同步观测: " + state + " + " + observationName);
        }
        return transition;
    }

    public HiveRecord discoverRemoteObject(FileCategoryEntity category, HiveOssObject object) {
        HiveRecord record = new HiveRecord();
        record.setFileName("");
        record.setFileKey(object.getFileKey());
        record.setZipped(false);
        record.setProvider(HiveStorageProvider.ALIBABA);
        record.setCategory(category);
        record.setSize(object.getSize());
        record.setUpdateTime(toLocalDateTime(object));
        transition(record, Event.DISCOVER_REMOTE_OBJECT);
        record.setLastSyncTime(LocalDateTime.now());
        record.setDeleted(false);
        record.setDeletable(false);
        return record;
    }

    private void applySyncEffect(HiveRecord record, SyncEffect effect, FileCategoryEntity category,
                                 HiveOssObject object, CategoryStorageClass storageClass) {
        switch (effect) {
            case NONE -> {
            }
            case REFRESH_REMOTE_METADATA -> {
                refreshRemoteMetadata(record, category, object);
                record.setDeletable(isRemoteDeletable(record, storageClass));
            }
            case REFRESH_REMOTE_METADATA_NOT_DELETABLE -> {
                refreshRemoteMetadata(record, category, object);
                record.setDeletable(false);
            }
            case TOUCH_MISSING_NOT_DELETABLE -> {
                record.setLastSyncTime(LocalDateTime.now());
                record.setDeletable(false);
            }
        }
    }

    private void refreshRemoteMetadata(HiveRecord record, FileCategoryEntity category, HiveOssObject object) {
        record.setProvider(HiveStorageProvider.ALIBABA);
        record.setCategory(category);
        record.setSize(object.getSize());
        record.setUpdateTime(toLocalDateTime(object));
        record.setLastSyncTime(LocalDateTime.now());
    }

    private LocalDateTime toLocalDateTime(HiveOssObject object) {
        return object.getLastModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private boolean isRemoteDeletable(HiveRecord record, CategoryStorageClass storageClass) {
        if (storageClass != CategoryStorageClass.ARCHIVE) {
            return true;
        }
        return record.getUpdateTime() != null
                && Duration.between(record.getUpdateTime(), LocalDateTime.now()).toDays() > 61;
    }
}
