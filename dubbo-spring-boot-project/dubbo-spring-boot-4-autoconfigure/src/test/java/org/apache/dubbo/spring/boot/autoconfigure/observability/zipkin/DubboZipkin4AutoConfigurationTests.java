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
package org.apache.dubbo.spring.boot.autoconfigure.observability.zipkin;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Call;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import static org.assertj.core.api.Assertions.assertThat;

class DubboZipkin4AutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DubboZipkin4AutoConfiguration.class))
            .withPropertyValues(
                    "dubbo.tracing.enabled=true",
                    "dubbo.tracing.tracing-exporter.zipkin-config.endpoint=http://localhost:9411/api/v2/spans");

    @Test
    void shouldSupplyZipkinBeansOnSpringBoot4() {
        this.contextRunner
                .withUserConfiguration(NoOpReporterConfiguration.class, NoOpSenderConfiguration.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(Sender.class);
                    assertThat(context).hasSingleBean(ZipkinSpanExporter.class);
                });
    }

    @Test
    void shouldSupplyRestTemplateSenderWhenUrlConnectionAndWebClientAreUnavailable() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(URLConnectionSender.class, WebClient.class))
                .withUserConfiguration(NoOpReporterConfiguration.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(Sender.class);
                    assertThat(context.getBean(Sender.class)).isInstanceOf(Zipkin4RestTemplateSender.class);
                    assertThat(context).hasSingleBean(ZipkinSpanExporter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    private static class NoOpReporterConfiguration {

        @Bean
        AsyncReporter<Span> spanReporter(Sender sender, zipkin2.reporter.BytesEncoder<Span> encoder) {
            return AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.NANOSECONDS)
                    .build(encoder);
        }
    }

    @Configuration(proxyBeanMethods = false)
    private static class NoOpSenderConfiguration {

        @Bean
        Sender sender() {
            return new Sender() {
                @Override
                public Encoding encoding() {
                    return Encoding.JSON;
                }

                @Override
                public int messageMaxBytes() {
                    return Integer.MAX_VALUE;
                }

                @Override
                public int messageSizeInBytes(List<byte[]> encodedSpans) {
                    return encodedSpans.stream().mapToInt(bytes -> bytes.length).sum();
                }

                @Override
                public Call<Void> sendSpans(List<byte[]> encodedSpans) {
                    return Call.create(null);
                }
            };
        }
    }
}
