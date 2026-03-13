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
package org.apache.dubbo.metrics.utils;

import org.apache.dubbo.common.utils.ClassUtils;

public class MetricsSupportUtil {

    private static final String[] PROMETHEUS_CONFIG_CLASSES = {"io.micrometer.prometheus.PrometheusConfig", "io.micrometer.prometheusmetrics.PrometheusConfig"};

    private static final String[] PROMETHEUS_PUSH_GATEWAY_CLASSES = {"io.prometheus.client.exporter.PushGateway", "io.prometheus.metrics.exporter.pushgateway.PushGateway"};

    private static final String[] PROMETHEUS_BASIC_AUTH_CONNECTION_FACTORY_CLASSES = {"io.prometheus.client.exporter.BasicAuthHttpConnectionFactory", "io.prometheus.metrics.exporter.httpurlconnection.BasicAuthHttpConnectionFactory"};

    private static final String[] PROMETHEUS_HTTP_CONNECTION_FACTORY_CLASSES = {"io.prometheus.client.exporter.HttpConnectionFactory", "io.prometheus.metrics.exporter.httpurlconnection.HttpConnectionFactory"};

    public static boolean isSupportMetrics() {
        return isClassPresent("io.micrometer.core.instrument.MeterRegistry");
    }

    public static boolean isSupportPrometheus() {
        return isAnyClassPresent(PROMETHEUS_CONFIG_CLASSES)
                && isAnyClassPresent(PROMETHEUS_BASIC_AUTH_CONNECTION_FACTORY_CLASSES)
                && isAnyClassPresent(PROMETHEUS_HTTP_CONNECTION_FACTORY_CLASSES)
                && isAnyClassPresent(PROMETHEUS_PUSH_GATEWAY_CLASSES);
    }

    private static boolean isClassPresent(String className) {
        return ClassUtils.isPresent(className, MetricsSupportUtil.class.getClassLoader());
    }

    private static boolean isAnyClassPresent(String... classNames) {
        for (String className : classNames) {
            if (isClassPresent(className)) {
                return true;
            }
        }
        return false;
    }
}
