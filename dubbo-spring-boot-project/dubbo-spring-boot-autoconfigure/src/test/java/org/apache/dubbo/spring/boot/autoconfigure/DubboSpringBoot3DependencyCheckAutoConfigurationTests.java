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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DubboSpringBoot3DependencyCheckAutoConfigurationTests {

    private static final String MISSING_BOOT3_AUTOCONFIGURE =
            "Couldn't enable servlet support for triple at SpringBoot3: Missing dubbo-spring-boot-3-autoconfigure";

    private final boolean springBoot3 = SpringBoot3Condition.IS_SPRING_BOOT_3;

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DubboSpringBoot3DependencyCheckAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(DubboTriple3AutoConfiguration.class));

    private final WebApplicationContextRunner contextRunnerWithSpringBoot3Autoconfigure =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DubboSpringBoot3DependencyCheckAutoConfiguration.class));

    @BeforeEach
    void enableSpringBoot3Condition() {
        SpringBoot3Condition.IS_SPRING_BOOT_3 = true;
    }

    @AfterEach
    void resetSpringBoot3Condition() {
        SpringBoot3Condition.IS_SPRING_BOOT_3 = springBoot3;
    }

    @Test
    void shouldFailWhenTripleServletEnabledWithoutSpringBoot3Autoconfigure() {
        this.contextRunner
                .withPropertyValues("dubbo.protocol.triple.servlet.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(MISSING_BOOT3_AUTOCONFIGURE);
                });
    }

    @Test
    void shouldFailWhenTripleWebSocketEnabledWithoutSpringBoot3Autoconfigure() {
        this.contextRunner
                .withPropertyValues("dubbo.protocol.triple.websocket.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(MISSING_BOOT3_AUTOCONFIGURE);
                });
    }

    @Test
    void shouldNotFailWhenSpringBoot3AutoconfigureIsPresent() {
        this.contextRunnerWithSpringBoot3Autoconfigure
                .withPropertyValues(
                        "dubbo.protocol.triple.servlet.enabled=true", "dubbo.protocol.triple.websocket.enabled=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldNotFailWhenSpringBoot3ConditionIsDisabled() {
        SpringBoot3Condition.IS_SPRING_BOOT_3 = false;

        this.contextRunner
                .withPropertyValues(
                        "dubbo.protocol.triple.servlet.enabled=true", "dubbo.protocol.triple.websocket.enabled=true")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
