package com.prelude.assets.infrastructure;

import com.prelude.assets.ObjectStoragePort;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Duration;

/**
 * The only implementation of ObjectStoragePort. Speaks the S3-compatible API
 * against any verified endpoint; local/CI uses VersityGW.
 */
@Slf4j
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3StorageConfiguration.S3StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3ObjectStorageAdapter(S3StorageConfiguration.S3StorageProperties properties) {
        this.properties = properties;
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        S3Configuration serviceConfiguration = S3Configuration.builder()
            .pathStyleAccessEnabled(properties.pathStyle())
            .build();
        this.s3Client = S3Client.builder()
            .region(Region.of(properties.region()))
            .endpointOverride(URI.create(properties.endpoint()))
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(serviceConfiguration)
            .httpClient(UrlConnectionHttpClient.builder().build())
            .build();
        this.presigner = S3Presigner.builder()
            .region(Region.of(properties.region()))
            .endpointOverride(URI.create(properties.publicEndpoint()))
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(serviceConfiguration)
            .build();
        ensureBucketExists();
    }

    /**
     * Local/CI gateways do not pre-create buckets. A gateway that is temporarily
     * unreachable must not block application startup; the failure then surfaces
     * on the first actual object operation.
     */
    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException missing) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (RuntimeException unreachable) {
            log.warn("Could not verify bucket {}; object operations will validate it on use",
                properties.bucket());
        }
    }

    @Override
    public void put(String objectKey, String mediaType, byte[] bytes) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(mediaType)
            .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (NoSuchBucketException missing) {
            // Bounded self-heal for a bucket that appeared missing (fresh gateway volume,
            // deferred startup): create it once and retry the same put. Only NoSuchBucket
            // is healed; timeouts, auth failures and 5xx propagate.
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        }
    }

    @Override
    public byte[] get(String objectKey) {
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .build());
        return responseBytes.asByteArray();
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .build());
    }

    @Override
    public String presignGet(String objectKey, Duration ttl) {
        PresignedGetObjectRequest request = presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build())
            .build());
        return request.url().toString();
    }
}
