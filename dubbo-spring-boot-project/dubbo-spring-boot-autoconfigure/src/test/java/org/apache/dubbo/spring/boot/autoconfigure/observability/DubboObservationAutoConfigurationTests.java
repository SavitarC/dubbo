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
package org.apache.dubbo.spring.boot.autoconfigure.observability;

import org.apache.dubbo.rpc.model.ApplicationModel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DubboObservationAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DubboObservationAutoConfiguration.class))
            .withUserConfiguration(ApplicationModelConfiguration.class)
            .withPropertyValues("dubbo.tracing.enabled=true");

    @Test
    void shouldBackOffObservationRegistryPostProcessorWhenSpringBoot4ProvidesOne() {
        this.contextRunner
                .withUserConfiguration(SpringBoot4ObservationConfiguration.class)
                .run((context) -> {
                    assertThat(context)
                            .hasSingleBean(
                                    org.springframework.boot.micrometer.observation.autoconfigure
                                            .ObservationRegistryPostProcessor.class);
                    assertThat(context).doesNotHaveBean(ObservationRegistryPostProcessor.class);
                    assertThat(context).doesNotHaveBean(ObservationHandlerGrouping.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    private static class ApplicationModelConfiguration {

        @Bean
        ApplicationModel applicationModel() {
            return mock(ApplicationModel.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    private static class SpringBoot4ObservationConfiguration {

        @Bean
        org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryPostProcessor
                springBoot4ObservationRegistryPostProcessor() {
            return new org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryPostProcessor();
        }
    }
}
