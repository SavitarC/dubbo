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
package org.apache.dubbo.metrics.prometheus;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.metrics.report.AbstractMetricsReporter;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.BasicAuthHttpConnectionFactory;
import io.prometheus.client.exporter.PushGateway;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.COMMON_METRICS_COLLECTOR_EXCEPTION;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_DEFAULT_JOB_NAME;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_DEFAULT_PUSH_INTERVAL;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_PUSHGATEWAY_BASE_URL_KEY;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_PUSHGATEWAY_ENABLED_KEY;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_PUSHGATEWAY_JOB_KEY;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_PUSHGATEWAY_PASSWORD_KEY;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_PUSHGATEWAY_PUSH_INTERVAL_KEY;
import static org.apache.dubbo.common.constants.MetricsConstants.PROMETHEUS_PUSHGATEWAY_USERNAME_KEY;

/**
 * Metrics reporter for prometheus.
 */
public class PrometheusMetricsReporter extends AbstractMetricsReporter {

    private final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(PrometheusMetricsReporter.class);

    private final PrometheusClientAdapter adapter;
    private ScheduledExecutorService pushJobExecutor = null;

    public PrometheusMetricsReporter(URL url, ApplicationModel applicationModel) {
        super(url, applicationModel);
        this.adapter = createAdapter();
    }

    private static boolean isClassPresent(String className) {
        return ClassUtils.isPresent(className, PrometheusMetricsReporter.class.getClassLoader());
    }

    @Override
    public void doInit() {
        addMeterRegistry(adapter.getMeterRegistry());
        schedulePushJob();
    }

    @Override
    public String getResponse() {
        return adapter.scrape();
    }

    private void schedulePushJob() {
        boolean pushEnabled = url.getParameter(PROMETHEUS_PUSHGATEWAY_ENABLED_KEY, false);
        if (pushEnabled) {
            String baseUrl = url.getParameter(PROMETHEUS_PUSHGATEWAY_BASE_URL_KEY);
            String job = url.getParameter(PROMETHEUS_PUSHGATEWAY_JOB_KEY, PROMETHEUS_DEFAULT_JOB_NAME);
            int pushInterval = url.getParameter(PROMETHEUS_PUSHGATEWAY_PUSH_INTERVAL_KEY, PROMETHEUS_DEFAULT_PUSH_INTERVAL);
            String username = url.getParameter(PROMETHEUS_PUSHGATEWAY_USERNAME_KEY);
            String password = url.getParameter(PROMETHEUS_PUSHGATEWAY_PASSWORD_KEY);

            NamedThreadFactory threadFactory = new NamedThreadFactory("prometheus-push-job", true);
            pushJobExecutor = Executors.newScheduledThreadPool(1, threadFactory);

            Object pushGateway = adapter.createPushGateway(baseUrl);
            if (!StringUtils.isBlank(username)) {
                adapter.setConnectionFactory(pushGateway, username, password);
            }

            pushJobExecutor.scheduleWithFixedDelay(() -> push(pushGateway, job), pushInterval, pushInterval, TimeUnit.SECONDS);
        }
    }

    @Override
    public void doDestroy() {

        if (pushJobExecutor != null) {
            pushJobExecutor.shutdownNow();
        }
    }

    /**
     * ut only
     */
    @Deprecated
    public ScheduledExecutorService getPushJobExecutor() {
        return pushJobExecutor;
    }

    protected void push(Object pushGateway, String job) {
        try {
            resetIfSamplesChanged();
            adapter.pushAdd(pushGateway, job);
        } catch (IOException e) {
            logger.error(COMMON_METRICS_COLLECTOR_EXCEPTION, "", "", "Error occurred when pushing metrics to prometheus: ", e);
        }
    }

    /**
     * ut only
     */
    @Deprecated
    public MeterRegistry getPrometheusRegistry() {
        return adapter.getMeterRegistry();
    }

    private PrometheusClientAdapter createAdapter() {
        if (NewPrometheusClientAdapter.isAvailable()) {
            return new NewPrometheusClientAdapter();
        }
        if (LegacyPrometheusClientAdapter.isAvailable()) {
            return new LegacyPrometheusClientAdapter();
        }
        throw new IllegalStateException("No supported Prometheus client implementation found.");
    }

    private interface PrometheusClientAdapter {

        MeterRegistry getMeterRegistry();

        String scrape();

        Object createPushGateway(String baseUrl);

        void setConnectionFactory(Object pushGateway, String username, String password);

        void pushAdd(Object pushGateway, String job) throws IOException;
    }

    private static class LegacyPrometheusClientAdapter implements PrometheusClientAdapter {

        private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        static boolean isAvailable() {
            return isClassPresent("io.micrometer.prometheus.PrometheusMeterRegistry")
                    && isClassPresent("io.prometheus.client.exporter.PushGateway");
        }

        @Override
        public MeterRegistry getMeterRegistry() {
            return registry;
        }

        @Override
        public String scrape() {
            return registry.scrape();
        }

        @Override
        public Object createPushGateway(String baseUrl) {
            return new PushGateway(baseUrl);
        }

        @Override
        public void setConnectionFactory(Object pushGateway, String username, String password) {
            ((PushGateway) pushGateway).setConnectionFactory(new BasicAuthHttpConnectionFactory(username, password));
        }

        @Override
        public void pushAdd(Object pushGateway, String job) throws IOException {
            ((PushGateway) pushGateway).pushAdd(registry.getPrometheusRegistry(), job);
        }
    }

    private static class NewPrometheusClientAdapter implements PrometheusClientAdapter {

        private final io.micrometer.prometheusmetrics.PrometheusMeterRegistry registry = new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);

        static boolean isAvailable() {
            return isClassPresent("io.micrometer.prometheusmetrics.PrometheusMeterRegistry")
                    && isClassPresent("io.prometheus.metrics.exporter.pushgateway.PushGateway");
        }

        @Override
        public MeterRegistry getMeterRegistry() {
            return registry;
        }

        @Override
        public String scrape() {
            return registry.scrape();
        }

        @Override
        public Object createPushGateway(String baseUrl) {
            return io.prometheus.metrics.exporter.pushgateway.PushGateway.builder()
                    .registry(registry.getPrometheusRegistry())
                    .address(baseUrl);
        }

        @Override
        public void setConnectionFactory(Object pushGateway, String username, String password) {
            ((io.prometheus.metrics.exporter.pushgateway.PushGateway.Builder) pushGateway).basicAuth(username, password);
        }

        @Override
        public void pushAdd(Object pushGateway, String job) throws IOException {
            ((io.prometheus.metrics.exporter.pushgateway.PushGateway.Builder) pushGateway).job(job).build().pushAdd();
        }
    }
}
