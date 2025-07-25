package cc.cc3c.hive.oss.vendor.client.alibaba;

import cc.cc3c.hive.oss.vendor.DownloadProgressListener;
import cc.cc3c.hive.oss.vendor.client.HiveOssClient;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssPartUploadResult;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreStatus;
import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.internal.OSSHeaders;
import com.aliyun.oss.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AlibabaOssClient implements HiveOssClient {

    private OSS ossClient;

    @Autowired
    public AlibabaOssClient(AlibabaOssConfig config) {
        ClientConfiguration conf = new ClientConfiguration();
        ossClient = new OSSClient(config.getEndPoint(), config.getAccessKeyId(), config.getAccessKeySecret(), conf);
    }

    @Override
    public String initiateMultipartUpload(HiveOssTask task) {
        return ossClient.initiateMultipartUpload(new InitiateMultipartUploadRequest(task.getBucket(), task.getKey())).getUploadId();
    }

    @Override
    public String getExistingMultipartUploadId(HiveOssTask task) {
        ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(task.getBucket());
        List<MultipartUpload> multipartUploadList = ossClient.listMultipartUploads(listMultipartUploadsRequest).getMultipartUploads();
        for (MultipartUpload multipartUpload : multipartUploadList) {
            if (task.getKey().equals(multipartUpload.getKey())) {
                return multipartUpload.getUploadId();
            }
        }
        return null;
    }

    @Override
    public void listParts(HiveOssTask task) {
        ListPartsRequest listPartsRequest = new ListPartsRequest(task.getBucket(), task.getKey(), task.getUploadId());
        List<PartSummary> partSummaryList = ossClient.listParts(listPartsRequest).getParts();

        Map<Integer, String> partToEtagMap = partSummaryList.stream().collect(Collectors.toMap(PartSummary::getPartNumber, PartSummary::getETag));
        task.setUploadedMap(partToEtagMap);
    }

    @Override
    public HiveOssPartUploadResult uploadPart(HiveOssTask task, byte[] buffer, int read, int part) {
        UploadPartRequest uploadPartRequest = new UploadPartRequest();
        uploadPartRequest.setBucketName(task.getBucket());
        uploadPartRequest.setKey(task.getKey());
        uploadPartRequest.setUploadId(task.getUploadId());
        uploadPartRequest.setPartNumber(part);
        uploadPartRequest.setPartSize(read);
        uploadPartRequest.setInputStream(new ByteArrayInputStream(buffer));
        UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
        PartETag partETag = uploadPartResult.getPartETag();
        return new HiveOssPartUploadResult(partETag.getPartNumber(), partETag.getETag());
    }

    @Override
    public void completeMultipartUpload(HiveOssTask task) {
        List<PartETag> partETagList = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : task.getUploadedMap().entrySet()) {
            partETagList.add(new PartETag(entry.getKey(), entry.getValue()));
        }
        partETagList.sort(Comparator.comparingInt(PartETag::getPartNumber));
        ossClient.completeMultipartUpload(new CompleteMultipartUploadRequest(task.getBucket(), task.getKey(), task.getUploadId(), partETagList));
    }

    @Override
    public void restore(HiveOssTask task) {
        ossClient.restoreObject(task.getBucket(), task.getKey());
    }

    @Override
    public HiveRestoreResult restoreCheck(HiveOssTask task) {
        ObjectMetadata objectMetadata = ossClient.getObjectMetadata(task.getBucket(), task.getKey());
        String restoreString = objectMetadata.getObjectRawRestore();
        if (restoreString == null) {
            return HiveRestoreResult.builder().restoreStatus(HiveRestoreStatus.NOT_STARTED).build();
        }
        if (restoreString.equals(OSSHeaders.OSS_ONGOING_RESTORE)) {
            return HiveRestoreResult.builder().restoreStatus(HiveRestoreStatus.IN_PROGRESS).build();
        }
        Pattern pattern = Pattern.compile("expiry-date=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(restoreString);
        if (matcher.find()) {
            String expiryDateStr = matcher.group(1);
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            ZonedDateTime expiryDate = ZonedDateTime.parse(expiryDateStr, formatter);
            return HiveRestoreResult.builder()
                    .restoreStatus(HiveRestoreStatus.COMPLETED)
                    .expiryDate(expiryDate)
                    .build();
        } else {
            throw new IllegalArgumentException("bad restore time");
        }
    }

    @Override
    public boolean doesObjectExist(HiveOssTask task) {
        return ossClient.doesObjectExist(task.getBucket(), task.getKey());
    }

    @Override
    public HiveOssObject getObject(HiveOssTask task) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(task.getBucket(), task.getKey());
        getObjectRequest.setProgressListener(new DownloadProgressListener(task));
        OSSObject ossObject = ossClient.getObject(getObjectRequest);
        HiveOssObject hiveOssObject = new HiveOssObject();
        hiveOssObject.setFileKey(ossObject.getKey());
        hiveOssObject.setSize(ossObject.getObjectMetadata().getContentLength());
        hiveOssObject.setLastModified(ossObject.getObjectMetadata().getLastModified());
        hiveOssObject.setStorageClass(ossObject.getObjectMetadata().getObjectStorageClass().toString());
        hiveOssObject.setObjectContent(ossObject.getObjectContent());
        return hiveOssObject;
    }

    @Override
    public List<HiveOssObject> listObject(HiveOssTask task) {
        List<HiveOssObject> objectSummaryList = new ArrayList<>();
        ObjectListing objectListing;
        String nextMarker = null;

        do {
            objectListing = ossClient.listObjects(new ListObjectsRequest(task.getBucket()).withMarker(nextMarker));

            for (OSSObjectSummary summary : objectListing.getObjectSummaries()) {
                HiveOssObject hiveOssObject = new HiveOssObject();
                hiveOssObject.setFileKey(summary.getKey());
                hiveOssObject.setSize(summary.getSize());
                hiveOssObject.setLastModified(summary.getLastModified());
                hiveOssObject.setStorageClass(summary.getStorageClass());
                objectSummaryList.add(hiveOssObject);
            }

            nextMarker = objectListing.getNextMarker();

        } while (objectListing.isTruncated());

        return objectSummaryList;
    }

    @Override
    public void deleteObject(HiveOssTask task) {
        ossClient.deleteObject(task.getBucket(), task.getKey());
    }
}
