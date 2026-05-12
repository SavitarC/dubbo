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

import org.apache.dubbo.spring.boot.autoconfigure.SpringBoot4Condition;
import org.apache.dubbo.spring.boot.autoconfigure.observability.annotation.ConditionalOnDubboTracingEnable;
import org.apache.dubbo.spring.boot.autoconfigure.observability.zipkin.Zipkin4Configurations.BraveConfiguration;
import org.apache.dubbo.spring.boot.autoconfigure.observability.zipkin.Zipkin4Configurations.OpenTelemetryConfiguration;
import org.apache.dubbo.spring.boot.autoconfigure.observability.zipkin.Zipkin4Configurations.ReporterConfiguration;
import org.apache.dubbo.spring.boot.autoconfigure.observability.zipkin.Zipkin4Configurations.SenderConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.Sender;

import static org.apache.dubbo.spring.boot.autoconfigure.observability.ObservabilityUtils.DUBBO_TRACING_ZIPKIN_CONFIG_PREFIX;
import static org.apache.dubbo.spring.boot.util.DubboUtils.DUBBO_PREFIX;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Zipkin on Spring Boot 4.
 *
 * @since 3.3.7
 */
@ConditionalOnProperty(prefix = DUBBO_PREFIX, name = "enabled", matchIfMissing = true)
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.tracing.zipkin"
        })
@ConditionalOnClass(Sender.class)
@Import({
    SenderConfiguration.class,
    ReporterConfiguration.class,
    BraveConfiguration.class,
    OpenTelemetryConfiguration.class
})
@ConditionalOnDubboTracingEnable
@Conditional(SpringBoot4Condition.class)
public class DubboZipkin4AutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = DUBBO_TRACING_ZIPKIN_CONFIG_PREFIX, name = "endpoint")
    @ConditionalOnMissingBean
    public BytesEncoder<Span> spanBytesEncoder() {
        return SpanBytesEncoder.JSON_V2;
    }

    @Bean
    @ConditionalOnProperty(prefix = DUBBO_TRACING_ZIPKIN_CONFIG_PREFIX, name = "endpoint")
    @ConditionalOnMissingBean
    public zipkin2.reporter.BytesEncoder<Span> reporterBytesEncoder() {
        return zipkin2.reporter.SpanBytesEncoder.JSON_V2;
    }
}
