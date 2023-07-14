/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;

public class S3CamelTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public static AwsConnector awsConnector = AwsConnector.allServices();

  private static void waitAndClearSetupTraces(String queueUrl, String queueName,
      String bucketName) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.CreateQueue", queueUrl, queueName)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.s3(span, 0, "S3.CreateBucket", bucketName, "PUT", null)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.GetQueueAttributes", queueUrl)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.SetQueueAttributes", queueUrl)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.s3(span, 0, "S3.SetBucketNotificationConfiguration",
                    bucketName, "PUT", null)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl, null,
                    CONSUMER)
            )
    );
    testing.clearData();
  }

  @Test
  public void camelS3ProducerToCamelSqsConsumer() {
    String queueName = "s3SqsCamelTest";
    String bucketName = "bucket-test-s3-sqs-camel";

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector, S3Config.class,
            ImmutableMap.of("bucketName", bucketName, "queueName", queueName));

    String queueUrl = setupTestInfrastructure(queueName, bucketName);
    waitAndClearSetupTraces(queueUrl, queueName, bucketName);

    camelApp.start();
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ListQueues")
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.s3(span, 0, "S3.ListObjects", bucketName, "GET", null)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> CamelSpanAssertions.direct(span, "input"),
                span -> CamelSpanAssertions.s3(span, 1, trace.getSpan(0), bucketName),
                span -> AwsSpanAssertions.s3(span, 2, "S3.PutObject", bucketName, "PUT", trace.getSpan(1)),
                span -> AwsSpanAssertions.sqs(span, 3, "SQS.ReceiveMessage", queueUrl, null, CONSUMER, trace.getSpan(2)),
                span -> CamelSpanAssertions.sqsConsume(span, 4, queueName, trace.getSpan(2))
            ),
        // HTTP "client" receiver span, one per each SQS request
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl, null, CLIENT)
            ),
        // camel polling
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl, null, CONSUMER)
            ),
        // camel cleaning received msg
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.DeleteMessage", queueUrl)
            )
    );

    camelApp.stop();
    awsConnector.deleteBucket(bucketName);
    awsConnector.purgeQueue(queueUrl);
  }

  String setupTestInfrastructure(String queueName, String bucketName) {
    // setup infra
    String queueUrl = awsConnector.createQueue(queueName);
    awsConnector.createBucket(bucketName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn);

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl);

    return queueUrl;
  }
}
