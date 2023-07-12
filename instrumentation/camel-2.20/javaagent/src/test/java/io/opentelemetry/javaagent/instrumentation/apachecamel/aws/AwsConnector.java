/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.QueueConfiguration;
import com.amazonaws.services.s3.model.S3Event;
import com.amazonaws.services.s3.model.SetBucketNotificationConfigurationRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.EnumSet;

class AwsConnector {

  private AmazonSQSAsyncClient sqsClient;
  private AmazonS3Client s3Client;
//  private AmazonSNSAsyncClient snsClient;


  static AwsConnector allServices() throws NoSuchAlgorithmException, KeyManagementException {

    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return null;
          }
          @Override
          public void checkClientTrusted(X509Certificate[] certs, String authType) {
          }
          @Override
          public void checkServerTrusted(X509Certificate[] certs, String authType) {
          }
        }
    };

    AwsConnector awsConnector = new AwsConnector();

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAllCerts, new SecureRandom());

    // Set the SSL context as the default SSL socket factory
    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());


    awsConnector.sqsClient = (AmazonSQSAsyncClient) AmazonSQSAsyncClient.asyncBuilder()
        .withCredentials(new DefaultAWSCredentialsProviderChain())
        .build();

    awsConnector.s3Client = (AmazonS3Client) AmazonS3Client.builder()
        .withCredentials(new DefaultAWSCredentialsProviderChain())
        .build();

//    awsConnector.snsClient = (AmazonSNSAsyncClient) AmazonSNSAsyncClient.asyncBuilder()
//        .build();

    return awsConnector;
  }

  void createBucket(String bucketName) {
    logger.info("Create bucket ${bucketName}");
    s3Client.createBucket(bucketName);
  }

  String getQueueArn(String queueUrl) {
    logger.info("Get ARN for queue ${queueUrl}");
    return sqsClient.getQueueAttributes(
            new GetQueueAttributesRequest(queueUrl)
                .withAttributeNames("QueueArn")).getAttributes()
        .get("QueueArn");
  }

  private static String getSqsPolicy(String resource) {
    return String.format(
        "{\"Statement\": [{\"Effect\": \"Allow\", \"Principal\": \"*\", \"Action\": \"sqs:SendMessage\", \"Resource\": \"%s\"}]}",
        resource
    );
  }

  void setQueuePublishingPolicy(String queueUrl, String queueArn) {
    logger.info("Set policy for queue ${queueArn}");
    sqsClient.setQueueAttributes(queueUrl, Collections.singletonMap("Policy", getSqsPolicy(queueArn)));
  }

  void enableS3ToSqsNotifications(String bucketName, String sqsQueueArn) {
    logger.info("Enable notification for bucket ${bucketName} to queue ${sqsQueueArn}");
    BucketNotificationConfiguration notificationConfiguration = new BucketNotificationConfiguration();
    notificationConfiguration.addConfiguration("sqsQueueConfig",
        new QueueConfiguration(sqsQueueArn, EnumSet.of(S3Event.ObjectCreatedByPut)));
    s3Client.setBucketNotificationConfiguration(new SetBucketNotificationConfigurationRequest(
        bucketName, notificationConfiguration));
  }

  private static final Logger logger = LoggerFactory.getLogger(AwsConnector.class);

  private SQSRestServer sqsRestServer;

  static AwsConnector elasticMq() {
    AwsConnector awsConnector = new AwsConnector();
    int sqsPort = PortUtils.findOpenPort();
    awsConnector.sqsRestServer =
        SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start();

    AWSStaticCredentialsProvider credentials =
        new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"));
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq");
    awsConnector.sqsClient =
        (AmazonSQSAsyncClient) AmazonSQSAsyncClient.asyncBuilder()
            .withCredentials(credentials)
            .withEndpointConfiguration(endpointConfiguration)
            .build();

    return awsConnector;
  }

  String createQueue(String queueName) {
    logger.info("Create queue " + queueName);
    return sqsClient.createQueue(queueName).getQueueUrl();
  }

  void sendSampleMessage(String queueUrl) {
    SendMessageRequest send = new SendMessageRequest(queueUrl, "{\"type\": \"hello\"}");
    sqsClient.sendMessage(send);
  }

  void receiveMessage(String queueUrl) {
    logger.info("Receive message from queue " + queueUrl);
    sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl).withWaitTimeSeconds(20));
  }

  void disconnect() {
    if (sqsRestServer != null) {
      sqsRestServer.stopAndWait();
    }
  }

  AmazonSQS getSqsClient() {
    return sqsClient;
  }
}
