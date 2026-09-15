package br.com.garagem.ordemservico.foto.application;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
public class S3FotoStorage implements FotoStorage {
  private final S3Client client;
  private final String bucket;

  public S3FotoStorage(
      @Value("${app.storage.endpoint}") String endpoint,
      @Value("${app.storage.region}") String region,
      @Value("${app.storage.bucket}") String bucket,
      @Value("${app.storage.access-key}") String access,
      @Value("${app.storage.secret-key}") String secret) {
    this.bucket = bucket;
    this.client =
        S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)))
            .forcePathStyle(true)
            .build();
  }

  @Override
  public void put(String key, byte[] content, String type) {
    client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build(),
        RequestBody.fromBytes(content));
  }

  @Override
  public byte[] get(String key) {
    return client
        .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
        .asByteArray();
  }

  @Override
  public void delete(String key) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  @Override
  public boolean disponivel() {
    try {
      client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  @PreDestroy
  public void close() {
    client.close();
  }
}
