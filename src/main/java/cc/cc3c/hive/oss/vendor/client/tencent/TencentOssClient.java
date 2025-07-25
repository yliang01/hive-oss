package cc.cc3c.hive.oss.vendor.client.tencent;


import cc.cc3c.hive.oss.vendor.client.HiveOssClient;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssPartUploadResult;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TencentOssClient implements HiveOssClient {

    private COSClient ossClient;

    @Autowired
    public TencentOssClient(TencentOssConfig config) {
        COSCredentials credentials = new BasicCOSCredentials(config.getSecretId(), config.getSecretKey());
        Region region = new Region(config.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        ossClient = new COSClient(credentials, clientConfig);
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
        throw new UnsupportedOperationException();
    }

    @Override
    public HiveRestoreResult restoreCheck(HiveOssTask task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean doesObjectExist(HiveOssTask task) {
        return false;
    }

    @Override
    public HiveOssObject getObject(HiveOssTask task) {
        COSObject cosObject = ossClient.getObject(task.getBucket(), task.getKey());
        HiveOssObject hiveOssObject = new HiveOssObject();
        hiveOssObject.setFileKey(cosObject.getKey());
        hiveOssObject.setSize(cosObject.getObjectMetadata().getContentLength());
        hiveOssObject.setLastModified(cosObject.getObjectMetadata().getLastModified());
        hiveOssObject.setObjectContent(cosObject.getObjectContent());
        return hiveOssObject;
    }
    @Override
    public List<HiveOssObject> listObject(HiveOssTask task) {
        List<HiveOssObject> objectSummaryList = new ArrayList<>();
        ObjectListing objectListing;
        String nextMarker = null;

        do {
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(task.getBucket());
            request.setMarker(nextMarker);
            objectListing = ossClient.listObjects(request);

            for (COSObjectSummary summary : objectListing.getObjectSummaries()) {
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



