/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // InstanceProfileCredentialsProvider
abstract class AbstractAwsClientTest {

  static <T> T configureClient(T client) {
    return null;
  }

  private static final AWSCredentialsProviderChain CREDENTIALS_PROVIDER_CHAIN =
      new AWSCredentialsProviderChain(
          new EnvironmentVariableCredentialsProvider(),
          new SystemPropertiesCredentialsProvider(),
          new ProfileCredentialsProvider(),
          new InstanceProfileCredentialsProvider());

  private static final AWSStaticCredentialsProvider credentialsProvider =
      new AWSStaticCredentialsProvider(new AnonymousAWSCredentials());

  private static AwsClientBuilder.EndpointConfiguration endpoint;

  private static final MockWebServerExtension server = new MockWebServerExtension();

  @BeforeAll
  public static void setUp() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key");
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key");
    server.start();
    endpoint = new AwsClientBuilder.EndpointConfiguration("${server.httpUri()}", "us-west-2");
    server.beforeTestExecution(null);
  }

  @AfterAll
  public static void cleanUp() {
    System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY);
    System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY);
    server.stop();
  }

  @ParameterizedTest
  @MethodSource("providesArguments")
  void testSendRequestWithMockedResponse(Parameter parameter) {
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, parameter.body));

    Object response = parameter.call.apply(parameter.clientWrapper);

    assertThat(response).isNotNull();
  }

  private static class ClientWrapper {
    private final AmazonS3 s3Client;
    private final AmazonDynamoDBClient dynamoDBClient;
    private final AmazonKinesisClient kinesisClient;
    private final AmazonEC2Client ec2Client;
    private final AmazonRDSClient rdsClient;

    private ClientWrapper(
        AmazonS3 s3Client,
        AmazonDynamoDBClient dynamoDBClient,
        AmazonKinesisClient kinesisClient,
        AmazonEC2Client ec2Client,
        AmazonRDSClient rdsClient) {
      this.s3Client = s3Client;
      this.dynamoDBClient = dynamoDBClient;
      this.kinesisClient = kinesisClient;
      this.ec2Client = ec2Client;
      this.rdsClient = rdsClient;
    }

    public Bucket createBucket(String bucket) {
      AwsClientBuilder clientBuilder =
          AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true);
      AmazonS3Client s3Client =
          (AmazonS3Client)
              configureClient(clientBuilder)
                  .withEndpointConfiguration(endpoint)
                  .withCredentials(credentialsProvider)
                  .build();
      return s3Client.createBucket(bucket);
    }

    public S3Object getObject(String bucket, String key) {
      AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true);
      return s3Client.getObject(bucket, key);
    }
  }

  private static Stream<Arguments> providesArguments() throws Exception {
    return Stream.of(
        Arguments.of(
            new Parameter(
                "S3",
                "CreateBucket",
                "PUT",
                "/testbucket/",
                new ClientWrapper(
                    AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true).build(),
                    null,
                    null,
                    null,
                    null),
                c -> c.createBucket("testbucket"),
                ImmutableMap.of("aws.bucket.name", "testbucket"),
                "")),
        Arguments.of(
            new Parameter(
                "S3",
                "GetObject",
                "GET",
                "/someBucket/someKey",
                new ClientWrapper(
                    AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true).build(),
                    null,
                    null,
                    null,
                    null),
                c -> c.getObject("someBucket", "someKey"),
                ImmutableMap.of("aws.bucket.name", "testbucket"),
                "")));
  }

  private static class Parameter {
    public final String service;
    public final String operation;
    public final String method;
    public final String path;

    public final ClientWrapper clientWrapper;

    //    public final T clientBuilder;
    public final Function<ClientWrapper, Object> call;

    public final ImmutableMap<String, String> additionalAttributes;
    public final String body;

    private Parameter(
        String service,
        String operation,
        String method,
        String path,
        ClientWrapper clientWrapper,
        Function<ClientWrapper, Object> call,
        ImmutableMap<String, String> additionalAttributes,
        String body) {
      this.service = service;
      this.operation = operation;
      this.method = method;
      this.path = path;
      this.clientWrapper = clientWrapper;
      //      this.clientBuilder = clientBuilder;
      this.call = call;
      this.additionalAttributes = additionalAttributes;
      this.body = body;
    }
  }
}
