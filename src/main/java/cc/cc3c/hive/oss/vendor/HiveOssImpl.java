package cc.cc3c.hive.oss.vendor;

import cc.cc3c.hive.oss.vendor.client.HiveOssClient;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssPartUploadResult;
import cc.cc3c.hive.oss.vendor.vo.HiveOssDownloadTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssUploadTask;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Backoff;
import reactor.retry.Repeat;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class HiveOssImpl implements HiveOss, InitializingBean {

    private final HiveOssClient ossClient;

    @Value("${hive.oss.part.size}")
    private int partSize;
    private int partSizeInByte;

    @Value("${hive.oss.concurrency}")
    private int concurrency;

    private Scheduler readerScheduler;
    private Scheduler uploadScheduler;
    private Scheduler restoreScheduler;
    private Scheduler downloadScheduler;

    @Autowired
    public HiveOssImpl(HiveOssClient ossClient) {
        this.ossClient = ossClient;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        partSizeInByte = partSize * 1024 * 1024;
        readerScheduler = Schedulers.newParallel("reader", concurrency, false);
        uploadScheduler = Schedulers.newParallel("upload", concurrency, false);
        restoreScheduler = Schedulers.newParallel("restore", concurrency, false);
        downloadScheduler = Schedulers.newParallel("download", concurrency, false);

    }

    @Override
    public List<HiveOssObject> listObjects(HiveOssTask task) throws Exception {
        return ossClient.listObject(task);
    }

    @Override
    public void upload(HiveOssUploadTask task) throws Exception {
        Hooks.onErrorDropped(x -> {
        });
        preMultipartUpload(task);
        multiPartsUpload(task);
    }

    private void preMultipartUpload(HiveOssUploadTask task) {
        String uploadId = ossClient.getExistingMultipartUploadId(task);
        if (uploadId != null) {
            task.setUploadId(uploadId);
            ossClient.listParts(task);
        } else {
            uploadId = ossClient.initiateMultipartUpload(task);
            task.setUploadId(uploadId);
        }
    }

    private ParallelFlux<DataChunk> getUploadFlux(HiveOssUploadTask task, InputStream inputStream) {
        return Flux.<DataChunk>generate(synchronousSink -> {
            try {
                while (true) {
                    byte[] buffer = new byte[partSizeInByte];
                    int read = IOUtils.read(inputStream, buffer, 0, buffer.length);
                    if (read == 0) {
                        synchronousSink.complete();
                        return;
                    } else {
                        int part = task.getCurrentPart().incrementAndGet();
                        if (task.getUploadedMap().containsKey(part)) {
                            log.info("ignore part {}", part);
                            continue;
                        }
                        log.info("start read part {}", part);
                        synchronousSink.next(new DataChunk(task, buffer, read, part));
                        log.info("end read part {}", part);
                        return;
                    }
                }
            } catch (Exception e) {
                synchronousSink.error(e);
            }
        }).subscribeOn(readerScheduler).parallel(concurrency, 1).runOn(uploadScheduler, 1);
    }

    private void subscribeUploadFlux(ParallelFlux<DataChunk> uploadFlux, HiveOssUploadTask task, ReentrantLock lock, Condition condition) {
        uploadFlux.doOnError(x -> log.error("parallel upload failed", x))
        .map(this::partUpload)
        .collectSortedList(Comparator.comparingInt(HiveOssPartUploadResult::getPart))
        .doOnSuccess(x -> {
            ossClient.listParts(task);
            ossClient.completeMultipartUpload(task);
        })
        .doFinally(signalType -> {
            try {
                lock.lock();
                log.info("upload completed");
                condition.signal();
            } finally {
                lock.unlock();
            }
        })
        .subscribe();
    }

    private void multiPartsUpload(HiveOssUploadTask task) {
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        lock.lock();
        try (InputStream inputStream = getInputStream(task)) {
            ParallelFlux<DataChunk> uploadFlux = getUploadFlux(task, inputStream);
            subscribeUploadFlux(uploadFlux, task, lock, condition);
            condition.await();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private HiveOssPartUploadResult partUpload(DataChunk dataChunk) {
        AtomicReference<HiveOssPartUploadResult> uploadResult = new AtomicReference<>();
        Mono.fromSupplier(() -> {
            log.info("start upload part {}", dataChunk.part);
            HiveOssPartUploadResult hiveOssPartUploadResult = ossClient.uploadPart(dataChunk.task, dataChunk.buffer, dataChunk.read, dataChunk.part);
            log.info("end upload part {}", dataChunk.part);
            return hiveOssPartUploadResult;
        }).doOnError(throwable -> {
            log.error("partUpload failed", throwable);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })
        .retry()
        .doOnNext(uploadResult::set)
        .subscribe();
        return uploadResult.get();
    }

    @Override
    public void restore(HiveOssTask task) throws Exception {
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        try {
            lock.lock();
            ossClient.restore(task);
            Mono.fromCallable(() -> {
                log.info("restore check");
                if (ossClient.isRestored(task)) {
                    log.info("restore successfully");
                    return task;
                } else {
                    return null;
                }
            })
            .subscribeOn(restoreScheduler)
            .repeatWhenEmpty(Repeat.onlyIf(r -> true)
                .backoff(Backoff.fixed(Duration.ofSeconds(10)))
                .timeout(Duration.ofMinutes(30)))
            .doOnError(throwable -> log.error("restore failed", throwable))
            .doFinally(signalType -> {
                lock.lock();
                condition.signal();
                lock.unlock();
            })
            .subscribe();

            condition.await();
            log.info("restored");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void download(HiveOssDownloadTask task) throws Exception {
        Hooks.onErrorDropped(x -> {
        });
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        try {
            lock.lock();
            Mono.fromCallable(() -> {
                log.info("download start");
                HiveOssObject ossObject = ossClient.getObject(task);
                try (InputStream inputStream = ossObject.getObjectContent();
                     OutputStream outputStream = getOutputStream(task)) {
                    IOUtils.copyLarge(inputStream, outputStream);
                }
                log.info("download successfully");
                return task;
            })
            .doOnError(throwable -> log.error("download failed", throwable))
            .doFinally(signalType -> {
                lock.lock();
                condition.signal();
                lock.unlock();
            })
            .subscribeOn(downloadScheduler)
            .subscribe();

            condition.await();
            log.info("downloaded");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(HiveOssTask task) {
        ossClient.deleteObject(task);
    }

    private InputStream getInputStream(HiveOssUploadTask task) throws Exception {
        InputStream inputStream;
        if (task.isEncrypted()) {
            inputStream = new CipherInputStream(new FileInputStream(task.getFile()), task.getEncryption().getEncryptCipher());
        } else {
            inputStream = new FileInputStream(task.getFile());
        }
        return inputStream;
    }

    private OutputStream getOutputStream(HiveOssDownloadTask task) throws Exception {
        OutputStream outputStream;
        if (task.isEncrypted()) {
            outputStream = new CipherOutputStream(new FileOutputStream(task.getFile()), task.getEncryption().getDecryptCipher());
        } else {
            outputStream = new FileOutputStream(task.getFile());
        }
        return outputStream;
    }

    @AllArgsConstructor
    private static class DataChunk {
        private final HiveOssUploadTask task;
        private final byte[] buffer;
        private final int read;
        private final int part;
    }
}
