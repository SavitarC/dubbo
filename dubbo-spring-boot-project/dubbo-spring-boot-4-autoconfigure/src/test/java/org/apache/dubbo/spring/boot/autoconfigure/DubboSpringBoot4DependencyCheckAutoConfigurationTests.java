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

import org.apache.dubbo.spring.boot.autoconfigure.observability.zipkin.DubboZipkin4AutoConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DubboSpringBoot4DependencyCheckAutoConfigurationTests {

    private static final String MISSING_BOOT4_AUTOCONFIGURE =
            "Couldn't enable servlet support for triple at SpringBoot4: Missing dubbo-spring-boot-4-autoconfigure";

    private static final String MISSING_BOOT4_ZIPKIN_AUTOCONFIGURE =
            "Couldn't enable Zipkin tracing at SpringBoot4: Missing dubbo-spring-boot-4-autoconfigure";

    private final boolean springBoot4 = SpringBoot4Condition.IS_SPRING_BOOT_4;

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DubboSpringBoot4DependencyCheckAutoConfiguration.class))
            .withClassLoader(
                    new FilteredClassLoader(DubboTriple4AutoConfiguration.class, DubboZipkin4AutoConfiguration.class));

    private final WebApplicationContextRunner contextRunnerWithSpringBoot4Autoconfigure =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DubboSpringBoot4DependencyCheckAutoConfiguration.class));

    @BeforeEach
    void enableSpringBoot4Condition() {
        SpringBoot4Condition.IS_SPRING_BOOT_4 = true;
    }

    @AfterEach
    void resetSpringBoot4Condition() {
        SpringBoot4Condition.IS_SPRING_BOOT_4 = springBoot4;
    }

    @Test
    void shouldFailWhenTripleServletEnabledWithoutSpringBoot4Autoconfigure() {
        this.contextRunner
                .withPropertyValues("dubbo.protocol.triple.servlet.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(MISSING_BOOT4_AUTOCONFIGURE);
                });
    }

    @Test
    void shouldFailWhenTripleWebSocketEnabledWithoutSpringBoot4Autoconfigure() {
        this.contextRunner
                .withPropertyValues("dubbo.protocol.triple.websocket.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(MISSING_BOOT4_AUTOCONFIGURE);
                });
    }

    @Test
    void shouldFailWhenZipkinEnabledWithoutSpringBoot4Autoconfigure() {
        this.contextRunner
                .withPropertyValues(
                        "dubbo.tracing.enabled=true",
                        "dubbo.tracing.tracing-exporter.zipkin-config.endpoint=http://localhost:9411/api/v2/spans")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(MISSING_BOOT4_ZIPKIN_AUTOCONFIGURE);
                });
    }

    @Test
    void shouldNotFailWhenSpringBoot4AutoconfigureIsPresent() {
        this.contextRunnerWithSpringBoot4Autoconfigure
                .withPropertyValues(
                        "dubbo.protocol.triple.servlet.enabled=true",
                        "dubbo.protocol.triple.websocket.enabled=true",
                        "dubbo.tracing.enabled=true",
                        "dubbo.tracing.tracing-exporter.zipkin-config.endpoint=http://localhost:9411/api/v2/spans")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldNotFailWhenSpringBoot4ConditionIsDisabled() {
        SpringBoot4Condition.IS_SPRING_BOOT_4 = false;

        this.contextRunner
                .withPropertyValues(
                        "dubbo.protocol.triple.servlet.enabled=true",
                        "dubbo.protocol.triple.websocket.enabled=true",
                        "dubbo.tracing.enabled=true",
                        "dubbo.tracing.tracing-exporter.zipkin-config.endpoint=http://localhost:9411/api/v2/spans")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldNotFailWhenZipkinEndpointIsMissing() {
        this.contextRunner.withPropertyValues("dubbo.tracing.enabled=true").run(context -> assertThat(context)
                .hasNotFailed());
    }

    @Test
    void shouldNotFailWhenTracingIsDisabled() {
        this.contextRunner
                .withPropertyValues(
                        "dubbo.tracing.enabled=false",
                        "dubbo.tracing.tracing-exporter.zipkin-config.endpoint=http://localhost:9411/api/v2/spans")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
