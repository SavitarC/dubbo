/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.spring.boot.autoconfigure;

import org.apache.dubbo.rpc.protocol.tri.servlet.jakarta.TripleFilter;
import org.apache.dubbo.rpc.protocol.tri.websocket.jakarta.TripleWebSocketFilter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class DubboTriple4AutoConfigurationTests {

    private final boolean springBoot4 = SpringBoot4Condition.IS_SPRING_BOOT_4;

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DubboTriple4AutoConfiguration.class));

    @BeforeEach
    void enableSpringBoot4Condition() {
        SpringBoot4Condition.IS_SPRING_BOOT_4 = true;
    }

    @AfterEach
    void resetSpringBoot4Condition() {
        SpringBoot4Condition.IS_SPRING_BOOT_4 = springBoot4;
    }

    @Test
    void shouldRegisterTripleServletFilter() {
        this.contextRunner
                .withPropertyValues(
                        "dubbo.protocol.triple.servlet.enabled=true",
                        "dubbo.protocol.triple.servlet.filter-url-patterns=/triple/*",
                        "dubbo.protocol.triple.servlet.filter-order=-100",
                        "server.port=20880")
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    FilterRegistrationBean<?> registrationBean = context.getBean(FilterRegistrationBean.class);
                    assertThat(registrationBean.getFilter()).isInstanceOf(TripleFilter.class);
                    assertThat(registrationBean.getUrlPatterns()).containsExactly("/triple/*");
                    assertThat(registrationBean.getOrder()).isEqualTo(-100);
                });
    }

    @Test
    void shouldRegisterTripleWebSocketFilter() {
        this.contextRunner
                .withPropertyValues(
                        "dubbo.protocol.triple.websocket.enabled=true",
                        "dubbo.protocol.triple.websocket.filter-url-patterns=/websocket/*",
                        "dubbo.protocol.triple.websocket.filter-order=-200",
                        "server.port=20881")
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    FilterRegistrationBean<?> registrationBean = context.getBean(FilterRegistrationBean.class);
                    assertThat(registrationBean.getFilter()).isInstanceOf(TripleWebSocketFilter.class);
                    assertThat(registrationBean.getUrlPatterns()).containsExactly("/websocket/*");
                    assertThat(registrationBean.getOrder()).isEqualTo(-200);
                });
    }

    @Test
    void shouldRegisterTomcatHttp2CustomizerWhenMaxConcurrentStreamsConfigured() {
        this.contextRunner
                .withPropertyValues(
                        "dubbo.protocol.triple.servlet.enabled=true",
                        "dubbo.protocol.triple.servlet.max-concurrent-streams=10")
                .run(context -> assertThat(context).hasSingleBean(WebServerFactoryCustomizer.class));
    }
}
