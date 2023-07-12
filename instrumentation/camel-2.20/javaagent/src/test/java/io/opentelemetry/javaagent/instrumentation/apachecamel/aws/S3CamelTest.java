/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class S3CamelTest {


  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();



  @AfterAll
  static void cleanUp() {

  }

//  private static void waitAndClearSetupTraces(String queueUrl, String queueName) {
//    testing.clearData();
//  }

  @Test
  public void setupAWS() throws NoSuchAlgorithmException, KeyManagementException {
    AwsConnector awsConnector = AwsConnector.allServices();
    String queueName = "test";
    String bucketName = "testbucket93093";
    String queueUrl = awsConnector.createQueue(queueName);
    awsConnector.createBucket(bucketName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn);
  }

//  def setupTestInfrastructure(queueName, bucketName) {
//    // setup infra
//    String queueUrl = awsConnector.createQueue(queueName)
//    awsConnector.createBucket(bucketName)
//    String queueArn = awsConnector.getQueueArn(queueUrl)
//    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
//    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn)
//
//    // consume test message from AWS
//    awsConnector.receiveMessage(queueUrl)
//
//    return queueUrl
//  }
//


}
