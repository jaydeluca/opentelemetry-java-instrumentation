/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTestUtil2.newDubboBootstrap;
import static io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTestUtil2.newFrameworkModel;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.runners.model.FrameworkMember;

class AbstractDubboTest2 {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ProtocolConfig protocolConfig = new ProtocolConfig();

//  @BeforeAll
//  protected void setUp() throws IOException {
//    NetUtils.setInterface(new MulticastSocket(Integer.parseInt(InetAddress.getLoopbackAddress().getAddress().toString())));
//  }


  private static ReferenceConfig<HelloService> configureCient(int port) {
    ReferenceConfig<HelloService> reference = new ReferenceConfig<>();
    reference.setInterface(HelloService.class);
    reference.setGeneric("true");
    reference.setUrl("dubbo://localhost:" + port + "/?timeout=30000");
    return reference;
  }

  private static ServiceConfig<HelloServiceImpl> configureServer() {
    RegistryConfig registryConfig = new RegistryConfig();
    registryConfig.setAddress("N/A");
    ServiceConfig<HelloServiceImpl> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setRef(new HelloServiceImpl());
    service.setRegistry(registryConfig);
    return service;
  }

  @Test
  void testApacheDubboBase() {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);

    FrameworkMember frameworkModel = (FrameworkMember) newFrameworkModel();
    DubboBootstrap bootstrap = newDubboBootstrap(frameworkModel);
    bootstrap.application(new ApplicationConfig("dubbo-test-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);

    ReferenceConfig<HelloService> reference = configureCient(port);
    DubboBootstrap consumerBootstrap = newDubboBootstrap(frameworkModel);
    consumerBootstrap.application(new ApplicationConfig("dubbo-demo-api-consumer"))
        .reference(reference)
        .protocol(consumerProtocolConfig)
        .start();

    GenericService genericService = (GenericService) reference.get();

    Object[] o = new Object[1];
    o[0] = "hello";

    String result = testing.runWithSpan(
        "parent",
        () -> (String) genericService.$invoke("hello", new String[]{String.class.getName()}, o));

    assertThat(result).isEqualTo("hello");
  }
}
