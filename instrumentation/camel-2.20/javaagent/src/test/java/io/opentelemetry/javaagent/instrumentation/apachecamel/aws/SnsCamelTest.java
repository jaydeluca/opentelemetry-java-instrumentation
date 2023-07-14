/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;

class SnsCamelTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final AwsConnector awsConnector = AwsConnector.allServices();

  private static void waitAndClearSetupTraces(String queueUrl, String queueName) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.CreateQueue", queueUrl, queueName)
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
                span -> AwsSpanAssertions.sns(span, 0, "SNS.CreateTopic", null)
            ),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, 0, "SNS.Subscribe", null)
            ),
        // test message
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)
            )
    );
    testing.clearData();
  }

  Map<String, String> setupTestInfrastructure(String queueName, String topicName) {
    // setup infra
    String queueUrl = awsConnector.createQueue(queueName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn);

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl);

    Map<String, String> metaData = new HashMap<>();
    metaData.put("queueUrl", queueUrl);
    metaData.put("topicArn", topicArn);
    return metaData;
  }

  @Test
  void test() {
    String topicName = "snsCamelTest";
    String queueName = "snsCamelTest";

//    CamelSpringApplication camelApp = new CamelSpringApplication(awsConnector, SnsConfig.class, ImmutableMap.of("topicName", topicName, "queueName", queueName));

    Map<String, String> metaData = setupTestInfrastructure(queueName, topicName);
    String queueUrl = metaData.get("queueUrl");
//    String topicArn = metaData.get("topicArn");
    waitAndClearSetupTraces(queueUrl, queueName);


  }
}
