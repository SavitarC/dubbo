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
package org.apache.dubbo.dependency;

import org.apache.dubbo.common.constants.CommonConstants;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileTest {
    private static final List<Pattern> ignoredModules = new LinkedList<>();
    private static final List<Pattern> ignoredArtifacts = new LinkedList<>();
    private static final List<Pattern> ignoredModulesInDubboAll = new LinkedList<>();
    private static final List<Pattern> ignoredModulesInDubboAllShade = new LinkedList<>();

    static {
        ignoredModules.add(Pattern.compile("dubbo-apache-release"));
        ignoredModules.add(Pattern.compile("dubbo-all-shaded"));
        ignoredModules.add(Pattern.compile("dubbo-dependencies-all"));
        ignoredModules.add(Pattern.compile("dubbo-parent"));
        ignoredModules.add(Pattern.compile("dubbo-core-spi"));
        ignoredModules.add(Pattern.compile("dubbo-demo.*"));
        ignoredModules.add(Pattern.compile("dubbo-annotation-processor"));
        ignoredModules.add(Pattern.compile("dubbo-config-spring6"));
        ignoredModules.add(Pattern.compile("dubbo-spring6-security"));
        ignoredModules.add(Pattern.compile("dubbo-spring-boot-3-autoconfigure"));
        ignoredModules.add(Pattern.compile("dubbo-spring-boot-4-autoconfigure"));
        ignoredModules.add(Pattern.compile("dubbo-spring-boot-4-starter"));
        ignoredModules.add(Pattern.compile("dubbo-plugin-loom.*"));
        ignoredModules.add(Pattern.compile("dubbo-mutiny.*"));
        ignoredModules.add(Pattern.compile("dubbo-mcp"));

        ignoredArtifacts.add(Pattern.compile("dubbo-demo.*"));
        ignoredArtifacts.add(Pattern.compile("dubbo-test.*"));
        ignoredArtifacts.add(Pattern.compile("dubbo-annotation-processor"));

        ignoredModulesInDubboAll.add(Pattern.compile("dubbo"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-all-shaded"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-bom"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-compiler"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-dependencies.*"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-distribution"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-metadata-processor"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-native.*"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-config-spring6.*"));
        ignoredModulesInDubboAll.add(Pattern.compile(".*spring-boot.*"));
        ignoredModulesInDubboAll.add(Pattern.compile("dubbo-maven-plugin"));

        ignoredModulesInDubboAllShade.add(Pattern.compile("dubbo-spring6-security"));
        ignoredModulesInDubboAllShade.add(Pattern.compile("dubbo-plugin-loom"));
        ignoredModulesInDubboAllShade.add(Pattern.compile("dubbo-mcp"));
        ignoredModulesInDubboAllShade.add(Pattern.compile("dubbo-mutiny"));
    }

    @Test
    void checkDubboBom() throws DocumentException {
        File baseFile = getBaseFile();

        List<File> poms = new LinkedList<>();
        readPoms(baseFile, poms);

        SAXReader reader = new SAXReader();

        List<String> artifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        String dubboBomPath = "dubbo-distribution" + File.separator + "dubbo-bom" + File.separator + "pom.xml";
        Document dubboBom = reader.read(new File(getBaseFile(), dubboBomPath));
        List<String> artifactIdsInDubboBom = dubboBom
                .getRootElement()
                .element("dependencyManagement")
                .element("dependencies")
                .elements("dependency")
                .stream()
                .map(ele -> ele.elementText("artifactId"))
                .collect(Collectors.toList());

        List<String> expectedArtifactIds = new LinkedList<>(artifactIds);
        expectedArtifactIds.removeAll(artifactIdsInDubboBom);
        expectedArtifactIds.removeIf(artifactId -> ignoredModules.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));

        Assertions.assertTrue(
                expectedArtifactIds.isEmpty(),
                "Newly created modules must be added to dubbo-bom. Found modules: " + expectedArtifactIds);
    }

    @Test
    void checkArtifacts() throws DocumentException, IOException {
        File baseFile = getBaseFile();

        List<File> poms = new LinkedList<>();
        readPoms(baseFile, poms);

        SAXReader reader = new SAXReader();

        List<String> artifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        List<String> artifactIdsInRoot = IOUtils.readLines(
                this.getClass()
                        .getClassLoader()
                        .getResource(CommonConstants.DUBBO_VERSIONS_KEY + "/.artifacts")
                        .openStream(),
                StandardCharsets.UTF_8);
        artifactIdsInRoot.removeIf(s -> s.startsWith("#"));

        List<String> expectedArtifactIds = new LinkedList<>(artifactIds);
        expectedArtifactIds.removeAll(artifactIdsInRoot);
        expectedArtifactIds.removeIf(artifactId -> ignoredArtifacts.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));

        Assertions.assertTrue(
                expectedArtifactIds.isEmpty(),
                "Newly created modules must be added to .artifacts (in project root). Found modules: "
                        + expectedArtifactIds);
    }

    @Test
    void checkDubboDependenciesAll() throws DocumentException {
        File baseFile = getBaseFile();

        List<File> poms = new LinkedList<>();
        readPoms(baseFile, poms);

        SAXReader reader = new SAXReader();

        List<String> artifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .filter(doc -> !Objects.equals("pom", doc.elementText("packaging")))
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        String dubboDependenciesAllPath =
                "dubbo-test" + File.separator + "dubbo-dependencies-all" + File.separator + "pom.xml";
        Document dubboDependenciesAll = reader.read(new File(getBaseFile(), dubboDependenciesAllPath));
        List<String> artifactIdsInDubboDependenciesAll =
                dubboDependenciesAll.getRootElement().element("dependencies").elements("dependency").stream()
                        .map(ele -> ele.elementText("artifactId"))
                        .collect(Collectors.toList());

        List<String> expectedArtifactIds = new LinkedList<>(artifactIds);
        expectedArtifactIds.removeAll(artifactIdsInDubboDependenciesAll);
        expectedArtifactIds.removeIf(artifactId -> ignoredModules.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));

        Assertions.assertTrue(
                expectedArtifactIds.isEmpty(),
                "Newly created modules must be added to dubbo-dependencies-all. Found modules: " + expectedArtifactIds);
    }

    @Test
    void checkZipkinSpringBootStartersSupportSpringBoot4() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        List<String> zipkinStarters = new LinkedList<>();
        zipkinStarters.add("dubbo-tracing-brave-zipkin-spring-boot-starter");
        zipkinStarters.add("dubbo-tracing-otel-zipkin-spring-boot-starter");

        for (String zipkinStarter : zipkinStarters) {
            String pomPath = "dubbo-spring-boot-project" + File.separator + "dubbo-spring-boot-starters"
                    + File.separator + zipkinStarter + File.separator + "pom.xml";
            Document document = reader.read(new File(baseFile, pomPath));

            Assertions.assertTrue(
                    hasProfileDependency(
                            document.getRootElement(), "spring-boot-4", "dubbo-spring-boot-4-autoconfigure"),
                    zipkinStarter + " must depend on dubbo-spring-boot-4-autoconfigure for Spring Boot 4");
            Assertions.assertFalse(
                    hasProfileDependency(
                            document.getRootElement(), "jdk-version-ge-17", "dubbo-spring-boot-4-autoconfigure"),
                    zipkinStarter + " must not enable Spring Boot 4 autoconfigure only because the JDK is 17+");
        }
    }

    @Test
    void checkSpringBootDemosSupportSpringBoot4SmokeCompile() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        List<String> demoPomPaths = new LinkedList<>();
        demoPomPaths.add("dubbo-demo" + File.separator + "dubbo-demo-spring-boot" + File.separator
                + "dubbo-demo-spring-boot-servlet" + File.separator + "pom.xml");
        demoPomPaths.add("dubbo-demo" + File.separator + "dubbo-demo-spring-boot-idl" + File.separator + "pom.xml");
        demoPomPaths.add("dubbo-demo" + File.separator + "dubbo-demo-mcp-server" + File.separator + "pom.xml");

        for (String demoPomPath : demoPomPaths) {
            Document document = reader.read(new File(baseFile, demoPomPath));

            Assertions.assertTrue(
                    hasProfileDependency(
                            document.getRootElement(), "spring-boot-4", "dubbo-spring-boot-4-autoconfigure"),
                    demoPomPath + " must depend on dubbo-spring-boot-4-autoconfigure for Spring Boot 4 smoke compile");
            Assertions.assertTrue(
                    hasProfileDependency(
                            document.getRootElement(), "spring-boot-3", "dubbo-spring-boot-3-autoconfigure"),
                    demoPomPath + " must depend on dubbo-spring-boot-3-autoconfigure only for Spring Boot 3");
            Assertions.assertFalse(
                    hasProfileDependency(
                            document.getRootElement(), "jdk-version-ge-17", "dubbo-spring-boot-3-autoconfigure"),
                    demoPomPath + " must not enable Spring Boot 3 autoconfigure only because the JDK is 17+");
        }
    }

    @Test
    void checkSpringBootIdlDemoUsesRequestedSpringBootProfiles() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String demoPomPath = "dubbo-demo" + File.separator + "dubbo-demo-spring-boot-idl" + File.separator + "pom.xml";
        Element rootElement = reader.read(new File(baseFile, demoPomPath)).getRootElement();

        Assertions.assertTrue(
                hasProfileProperty(rootElement, "spring-boot-3", "spring-boot.version", "${spring-boot-3.version}"),
                demoPomPath + " must use ${spring-boot-3.version} under spring-boot-3");
        Assertions.assertTrue(
                hasProfileProperty(rootElement, "spring-boot-4", "spring-boot.version", "${spring-boot-4.version}"),
                demoPomPath + " must use ${spring-boot-4.version} under spring-boot-4");
    }

    @Test
    void checkSpringBootDemoUsesRequestedSpringBootProfiles() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String demoPomPath = "dubbo-demo" + File.separator + "dubbo-demo-spring-boot" + File.separator + "pom.xml";
        Element rootElement = reader.read(new File(baseFile, demoPomPath)).getRootElement();

        Assertions.assertTrue(
                hasProfileProperty(rootElement, "spring-boot-3", "spring-boot.version", "${spring-boot-3.version}"),
                demoPomPath + " must use ${spring-boot-3.version} under spring-boot-3");
        Assertions.assertTrue(
                hasProfileProperty(
                        rootElement, "spring-boot-3", "spring-boot-maven-plugin.version", "${spring-boot-3.version}"),
                demoPomPath
                        + " must use ${spring-boot-3.version} for the Spring Boot Maven plugin under spring-boot-3");
        Assertions.assertTrue(
                hasProfileProperty(rootElement, "spring-boot-4", "spring-boot.version", "${spring-boot-4.version}"),
                demoPomPath + " must use ${spring-boot-4.version} under spring-boot-4");
        Assertions.assertTrue(
                hasProfileProperty(
                        rootElement, "spring-boot-4", "spring-boot-maven-plugin.version", "${spring-boot-4.version}"),
                demoPomPath
                        + " must use ${spring-boot-4.version} for the Spring Boot Maven plugin under spring-boot-4");
    }

    @Test
    void checkSpringBootMcpServerUsesRequestedSpringBootPluginProfiles() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String demoPomPath = "dubbo-demo" + File.separator + "dubbo-demo-mcp-server" + File.separator + "pom.xml";
        Element rootElement = reader.read(new File(baseFile, demoPomPath)).getRootElement();

        Assertions.assertTrue(
                hasProfileProperty(
                        rootElement, "spring-boot-3", "spring-boot-maven-plugin.version", "${spring-boot-3.version}"),
                demoPomPath
                        + " must use ${spring-boot-3.version} for the Spring Boot Maven plugin under spring-boot-3");
        Assertions.assertTrue(
                hasProfileProperty(
                        rootElement, "spring-boot-4", "spring-boot-maven-plugin.version", "${spring-boot-4.version}"),
                demoPomPath
                        + " must use ${spring-boot-4.version} for the Spring Boot Maven plugin under spring-boot-4");
    }

    @Test
    void checkSpringBootModulesKeepVersionedSourceProfiles() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String[][] modules = {
            {
                "dubbo-spring-boot-project" + File.separator + "dubbo-spring-boot" + File.separator + "pom.xml",
                "src/spring-boot-before-4/java",
                "src/spring-boot-4/java"
            },
            {
                "dubbo-spring-boot-project" + File.separator + "dubbo-spring-boot-actuator" + File.separator
                        + "pom.xml",
                "src/spring-boot-before-4/java",
                "src/spring-boot-4/java"
            },
            {
                "dubbo-plugin" + File.separator + "dubbo-rest-spring" + File.separator + "pom.xml",
                "src/spring-boot-before-4/java",
                "src/spring-boot-4/java"
            }
        };

        for (String[] module : modules) {
            Element rootElement = reader.read(new File(baseFile, module[0])).getRootElement();
            Assertions.assertTrue(
                    hasProfileSource(rootElement, "spring-boot-before-4", module[1]),
                    module[0] + " must keep its Spring Boot before 4 source set");
            Assertions.assertTrue(
                    hasProfileSource(rootElement, "spring-boot-4", module[2]),
                    module[0] + " must keep its Spring Boot 4 source set");
        }
    }

    @Test
    void checkDubboConfigSpringUsesSpringBoot4Profile() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String pomPath = "dubbo-config" + File.separator + "dubbo-config-spring" + File.separator + "pom.xml";
        Element rootElement = reader.read(new File(baseFile, pomPath)).getRootElement();

        Assertions.assertTrue(
                hasProfileProperty(rootElement, "spring-boot-4", "spring-boot.version", "${spring-boot-4.version}"),
                pomPath + " must use ${spring-boot-4.version} under spring-boot-4");
        Assertions.assertTrue(
                hasProfileProperty(rootElement, "spring-boot-4", "spring-boot.tomcat.version", "${tomcat.version}"),
                pomPath + " must use ${tomcat.version} under spring-boot-4");
    }

    @Test
    void checkDubboBomIncludesSpringBoot4Artifacts() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String bomPath = "dubbo-distribution" + File.separator + "dubbo-bom" + File.separator + "pom.xml";
        Element rootElement = reader.read(new File(baseFile, bomPath)).getRootElement();
        String[] artifactIds = {
            "dubbo-spring-boot-3-autoconfigure", "dubbo-spring-boot-4-autoconfigure", "dubbo-spring-boot-4-starter"
        };

        for (String artifactId : artifactIds) {
            Assertions.assertTrue(
                    hasManagedDependency(rootElement, "org.apache.dubbo", artifactId, "${project.version}"),
                    "dubbo-bom must include " + artifactId);
        }
    }

    @Test
    void checkSpringBootDependencyCheckAutoConfigurationsRegistered() throws IOException {
        File baseFile = getBaseFile();
        String autoConfigurationImportsPath = "dubbo-spring-boot-project" + File.separator
                + "dubbo-spring-boot-autoconfigure" + File.separator + "src" + File.separator + "main"
                + File.separator + "resources" + File.separator + "META-INF" + File.separator + "spring"
                + File.separator + "org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        String springFactoriesPath = "dubbo-spring-boot-project" + File.separator + "dubbo-spring-boot-autoconfigure"
                + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator
                + "META-INF" + File.separator + "spring.factories";

        String[] dependencyCheckAutoConfigurations = {
            "org.apache.dubbo.spring.boot.autoconfigure.DubboSpringBoot3DependencyCheckAutoConfiguration",
            "org.apache.dubbo.spring.boot.autoconfigure.DubboSpringBoot4DependencyCheckAutoConfiguration"
        };
        for (String autoConfiguration : dependencyCheckAutoConfigurations) {
            Assertions.assertTrue(
                    hasResourceEntry(baseFile, autoConfigurationImportsPath, autoConfiguration),
                    autoConfiguration + " must be registered in AutoConfiguration.imports");
            Assertions.assertTrue(
                    hasResourceEntry(baseFile, springFactoriesPath, autoConfiguration),
                    autoConfiguration + " must be registered in spring.factories");
        }
    }

    @Test
    void checkSpringBoot4AutoConfigurationResources() throws IOException {
        File baseFile = getBaseFile();
        String autoConfigurationImportsPath = "dubbo-spring-boot-project" + File.separator
                + "dubbo-spring-boot-4-autoconfigure" + File.separator + "src" + File.separator + "main"
                + File.separator + "resources" + File.separator + "META-INF" + File.separator + "spring"
                + File.separator + "org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        String springFactoriesPath = "dubbo-spring-boot-project" + File.separator + "dubbo-spring-boot-4-autoconfigure"
                + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator
                + "META-INF" + File.separator + "spring.factories";

        String[] springBoot4AutoConfigurations = {
            "org.apache.dubbo.spring.boot.autoconfigure.DubboTriple4AutoConfiguration",
            "org.apache.dubbo.spring.boot.autoconfigure.observability.zipkin.DubboZipkin4AutoConfiguration"
        };
        for (String autoConfiguration : springBoot4AutoConfigurations) {
            Assertions.assertTrue(
                    hasResourceEntry(baseFile, autoConfigurationImportsPath, autoConfiguration),
                    autoConfiguration + " must be registered in AutoConfiguration.imports");
            Assertions.assertTrue(
                    hasResourceEntry(baseFile, springFactoriesPath, autoConfiguration),
                    autoConfiguration + " must be registered in spring.factories");
        }
    }

    @Test
    void checkSpringBoot4StarterDependsOnAutoConfigurations() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String springBoot4StarterPath = "dubbo-spring-boot-project" + File.separator + "dubbo-spring-boot-starters"
                + File.separator + "dubbo-spring-boot-4-starter" + File.separator + "pom.xml";
        Element rootElement =
                reader.read(new File(baseFile, springBoot4StarterPath)).getRootElement();

        Assertions.assertTrue(
                hasDependency(rootElement, "dubbo-spring-boot-autoconfigure"),
                "dubbo-spring-boot-4-starter must depend on dubbo-spring-boot-autoconfigure");
        Assertions.assertTrue(
                hasDependency(rootElement, "dubbo-spring-boot-4-autoconfigure"),
                "dubbo-spring-boot-4-starter must depend on dubbo-spring-boot-4-autoconfigure");
    }

    @Test
    void checkDubboDependenciesAllSupportSpringBoot4() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String dubboDependenciesAllPath =
                "dubbo-test" + File.separator + "dubbo-dependencies-all" + File.separator + "pom.xml";
        Element rootElement =
                reader.read(new File(baseFile, dubboDependenciesAllPath)).getRootElement();

        Assertions.assertTrue(
                hasProfileDependency(rootElement, "jdk-version-ge-17", "dubbo-spring-boot-4-autoconfigure"),
                "dubbo-dependencies-all must include dubbo-spring-boot-4-autoconfigure on JDK 17+");
        Assertions.assertTrue(
                hasProfileDependency(rootElement, "jdk-version-ge-17", "dubbo-spring-boot-4-starter"),
                "dubbo-dependencies-all must include dubbo-spring-boot-4-starter on JDK 17+");
    }

    @Test
    void checkRootSpringBoot4Profile() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        Element rootElement = reader.read(new File(baseFile, "pom.xml")).getRootElement();

        Assertions.assertTrue(
                hasProfileManagedDependency(
                        rootElement, "spring-boot-4", "org.junit", "junit-bom", "${junit-bom.version}"),
                "spring-boot-4 profile must import junit-bom with ${junit-bom.version}");

        String[] junitJupiterArtifacts = {"junit-jupiter-api", "junit-jupiter-engine", "junit-jupiter-params"};
        for (String artifactId : junitJupiterArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement, "spring-boot-4", "org.junit.jupiter", artifactId, "${junit-bom.version}"),
                    "spring-boot-4 profile must manage " + artifactId + " with ${junit-bom.version}");
        }

        String[] junitPlatformArtifacts = {"junit-platform-commons", "junit-platform-engine", "junit-platform-launcher"
        };
        for (String artifactId : junitPlatformArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement, "spring-boot-4", "org.junit.platform", artifactId, "${junit-bom.version}"),
                    "spring-boot-4 profile must manage " + artifactId + " with ${junit-bom.version}");
        }

        String[] logbackArtifacts = {"logback-classic", "logback-core"};
        for (String artifactId : logbackArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement, "spring-boot-4", "ch.qos.logback", artifactId, "${logback-boot-4.version}"),
                    "spring-boot-4 profile must manage " + artifactId + " with ${logback-boot-4.version}");
        }

        String[] slf4jArtifacts = {"jcl-over-slf4j", "jul-to-slf4j", "log4j-over-slf4j", "slf4j-api"};
        for (String artifactId : slf4jArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement, "spring-boot-4", "org.slf4j", artifactId, "${slf4j-boot-4.version}"),
                    "spring-boot-4 profile must manage " + artifactId + " with ${slf4j-boot-4.version}");
        }

        Assertions.assertTrue(
                hasProfileManagedDependency(
                        rootElement,
                        "spring-boot-4",
                        "com.fasterxml.jackson.core",
                        "jackson-annotations",
                        "${jackson-annotations-boot-4.version}"),
                "spring-boot-4 profile must manage jackson-annotations with ${jackson-annotations-boot-4.version}");

        String[] springBootArtifacts = {
            "spring-boot",
            "spring-boot-autoconfigure",
            "spring-boot-starter",
            "spring-boot-test",
            "spring-boot-test-autoconfigure",
            "spring-boot-starter-test",
            "spring-boot-actuator",
            "spring-boot-actuator-autoconfigure",
            "spring-boot-starter-actuator",
            "spring-boot-health",
            "spring-boot-micrometer-metrics",
            "spring-boot-configuration-processor",
            "spring-boot-http-client",
            "spring-boot-http-converter",
            "spring-boot-jackson",
            "spring-boot-restclient",
            "spring-boot-starter-aop",
            "spring-boot-starter-json",
            "spring-boot-starter-log4j2",
            "spring-boot-starter-logging",
            "spring-boot-starter-tomcat",
            "spring-boot-starter-validation",
            "spring-boot-starter-web",
            "spring-boot-tomcat",
            "spring-boot-web-server"
        };
        for (String artifactId : springBootArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement,
                            "spring-boot-4",
                            "org.springframework.boot",
                            artifactId,
                            "${spring-boot-4.version}"),
                    "spring-boot-4 profile must manage " + artifactId + " with ${spring-boot-4.version}");
        }

        String[] springFrameworkArtifacts = {
            "spring-aop",
            "spring-aspects",
            "spring-beans",
            "spring-context",
            "spring-context-indexer",
            "spring-context-support",
            "spring-core",
            "spring-core-test",
            "spring-expression",
            "spring-instrument",
            "spring-jdbc",
            "spring-test",
            "spring-tx",
            "spring-web",
            "spring-webflux",
            "spring-webmvc",
            "spring-websocket"
        };
        for (String artifactId : springFrameworkArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement, "spring-boot-4", "org.springframework", artifactId, "${spring-7.version}"),
                    "spring-boot-4 profile must manage " + artifactId + " with ${spring-7.version}");
        }

        String[] tomcatArtifacts = {"tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket"};
        for (String artifactId : tomcatArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement, "spring-boot-4", "org.apache.tomcat.embed", artifactId, "${tomcat.version}"),
                    "spring-boot-4 profile must manage " + artifactId + " with ${tomcat.version}");
        }
    }

    @Test
    void checkRootSpringBoot3Profile() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        Element rootElement = reader.read(new File(baseFile, "pom.xml")).getRootElement();

        String[] springBootArtifacts = {
            "spring-boot",
            "spring-boot-autoconfigure",
            "spring-boot-starter",
            "spring-boot-test",
            "spring-boot-test-autoconfigure",
            "spring-boot-starter-test",
            "spring-boot-actuator",
            "spring-boot-actuator-autoconfigure",
            "spring-boot-starter-actuator",
            "spring-boot-configuration-processor",
            "spring-boot-starter-aop",
            "spring-boot-starter-json",
            "spring-boot-starter-log4j2",
            "spring-boot-starter-logging",
            "spring-boot-starter-tomcat",
            "spring-boot-starter-validation",
            "spring-boot-starter-web"
        };
        for (String artifactId : springBootArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement,
                            "spring-boot-3",
                            "org.springframework.boot",
                            artifactId,
                            "${spring-boot-3.version}"),
                    "spring-boot-3 profile must manage " + artifactId + " with ${spring-boot-3.version}");
        }

        String[] tomcatArtifacts = {"tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket"};
        for (String artifactId : tomcatArtifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement, "spring-boot-3", "org.apache.tomcat.embed", artifactId, "${tomcat.version}"),
                    "spring-boot-3 profile must manage " + artifactId + " with ${tomcat.version}");
        }
    }

    @Test
    void checkSpringBootCompatibleKeepsSpringBoot1DependencyManagement() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String springBootCompatiblePath = "dubbo-spring-boot-project" + File.separator + "dubbo-spring-boot-compatible"
                + File.separator + "pom.xml";
        Element rootElement =
                reader.read(new File(baseFile, springBootCompatiblePath)).getRootElement();

        String[] springBoot1Artifacts = {
            "spring-boot",
            "spring-boot-autoconfigure",
            "spring-boot-configuration-processor",
            "spring-boot-starter",
            "spring-boot-test",
            "spring-boot-test-autoconfigure",
            "spring-boot-actuator",
            "spring-boot-starter-actuator",
            "spring-boot-starter-log4j2",
            "spring-boot-starter-tomcat",
            "spring-boot-starter-test",
            "spring-boot-starter-web"
        };
        for (String artifactId : springBoot1Artifacts) {
            Assertions.assertTrue(
                    hasManagedDependency(rootElement, "org.springframework.boot", artifactId, "${spring-boot.version}"),
                    "spring-boot-compatible must manage " + artifactId + " with ${spring-boot.version}");
        }

        String[] tomcat8Artifacts = {"tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket"};
        for (String artifactId : tomcat8Artifacts) {
            Assertions.assertTrue(
                    hasManagedDependency(
                            rootElement, "org.apache.tomcat.embed", artifactId, "${spring-boot.tomcat.version}"),
                    "spring-boot-compatible must manage " + artifactId + " with ${spring-boot.tomcat.version}");
        }
    }

    @Test
    void checkSpringBoot3AutoconfigureKeepsSpringBoot3DependencyManagement() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String springBoot3AutoconfigurePath = "dubbo-spring-boot-project" + File.separator
                + "dubbo-spring-boot-3-autoconfigure" + File.separator + "pom.xml";
        Element rootElement =
                reader.read(new File(baseFile, springBoot3AutoconfigurePath)).getRootElement();

        String[] springBoot3Artifacts = {
            "spring-boot",
            "spring-boot-autoconfigure",
            "spring-boot-starter",
            "spring-boot-test",
            "spring-boot-test-autoconfigure",
            "spring-boot-starter-test",
            "spring-boot-actuator",
            "spring-boot-actuator-autoconfigure",
            "spring-boot-starter-actuator",
            "spring-boot-configuration-processor",
            "spring-boot-starter-aop",
            "spring-boot-starter-json",
            "spring-boot-starter-log4j2",
            "spring-boot-starter-logging",
            "spring-boot-starter-tomcat",
            "spring-boot-starter-validation",
            "spring-boot-starter-web"
        };
        for (String artifactId : springBoot3Artifacts) {
            Assertions.assertTrue(
                    hasManagedDependency(
                            rootElement, "org.springframework.boot", artifactId, "${spring-boot-3.version}"),
                    "dubbo-spring-boot-3-autoconfigure must manage " + artifactId + " with ${spring-boot-3.version}");
        }
        String[] tomcat10Artifacts = {"tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket"};
        for (String artifactId : tomcat10Artifacts) {
            Assertions.assertTrue(
                    hasManagedDependency(
                            rootElement, "org.apache.tomcat.embed", artifactId, "${spring-boot-3.tomcat.version}"),
                    "dubbo-spring-boot-3-autoconfigure must manage " + artifactId
                            + " with ${spring-boot-3.tomcat.version}");
        }
    }

    @Test
    void checkSpringBootAutoconfigureKeepsBefore4DependencyManagement() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String springBootAutoconfigurePath = "dubbo-spring-boot-project" + File.separator
                + "dubbo-spring-boot-autoconfigure" + File.separator + "pom.xml";
        Element rootElement =
                reader.read(new File(baseFile, springBootAutoconfigurePath)).getRootElement();

        String[] profileIds = {"spring-boot-3", "spring-boot-4"};
        String[] springBootBefore4Artifacts = {
            "spring-boot",
            "spring-boot-autoconfigure",
            "spring-boot-starter",
            "spring-boot-test",
            "spring-boot-test-autoconfigure",
            "spring-boot-starter-test",
            "spring-boot-actuator",
            "spring-boot-actuator-autoconfigure",
            "spring-boot-starter-actuator",
            "spring-boot-configuration-processor",
            "spring-boot-starter-aop",
            "spring-boot-starter-json",
            "spring-boot-starter-log4j2",
            "spring-boot-starter-logging",
            "spring-boot-starter-tomcat",
            "spring-boot-starter-validation",
            "spring-boot-starter-web"
        };
        String[] tomcatBefore4Artifacts = {"tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket"};
        for (String profileId : profileIds) {
            for (String artifactId : springBootBefore4Artifacts) {
                Assertions.assertTrue(
                        hasProfileManagedDependency(
                                rootElement,
                                profileId,
                                "org.springframework.boot",
                                artifactId,
                                "${spring-boot-2.version}"),
                        "dubbo-spring-boot-autoconfigure must manage " + artifactId
                                + " with ${spring-boot-2.version} under " + profileId);
            }
            for (String artifactId : tomcatBefore4Artifacts) {
                Assertions.assertTrue(
                        hasProfileManagedDependency(
                                rootElement,
                                profileId,
                                "org.apache.tomcat.embed",
                                artifactId,
                                "${spring-boot-2.tomcat.version}"),
                        "dubbo-spring-boot-autoconfigure must manage " + artifactId
                                + " with ${spring-boot-2.tomcat.version} under " + profileId);
            }
        }
    }

    @Test
    void checkSpringBootActuatorAutoconfigureKeepsBefore4DependencyManagement() throws DocumentException {
        File baseFile = getBaseFile();
        SAXReader reader = new SAXReader();
        String springBootActuatorAutoconfigurePath = "dubbo-spring-boot-project" + File.separator
                + "dubbo-spring-boot-actuator-autoconfigure" + File.separator + "pom.xml";
        Element rootElement = reader.read(new File(baseFile, springBootActuatorAutoconfigurePath))
                .getRootElement();

        String[] springBootBefore4Artifacts = {
            "spring-boot",
            "spring-boot-autoconfigure",
            "spring-boot-starter",
            "spring-boot-test",
            "spring-boot-test-autoconfigure",
            "spring-boot-starter-test",
            "spring-boot-actuator",
            "spring-boot-actuator-autoconfigure",
            "spring-boot-starter-actuator",
            "spring-boot-configuration-processor",
            "spring-boot-starter-aop",
            "spring-boot-starter-json",
            "spring-boot-starter-log4j2",
            "spring-boot-starter-logging",
            "spring-boot-starter-tomcat",
            "spring-boot-starter-validation",
            "spring-boot-starter-web"
        };
        for (String artifactId : springBootBefore4Artifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement,
                            "spring-boot-3",
                            "org.springframework.boot",
                            artifactId,
                            "${spring-boot-2.version}"),
                    "dubbo-spring-boot-actuator-autoconfigure must manage " + artifactId
                            + " with ${spring-boot-2.version} under spring-boot-3");
        }

        String[] tomcatBefore4Artifacts = {"tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket"};
        for (String artifactId : tomcatBefore4Artifacts) {
            Assertions.assertTrue(
                    hasProfileManagedDependency(
                            rootElement,
                            "spring-boot-3",
                            "org.apache.tomcat.embed",
                            artifactId,
                            "${spring-boot-2.tomcat.version}"),
                    "dubbo-spring-boot-actuator-autoconfigure must manage " + artifactId
                            + " with ${spring-boot-2.tomcat.version} under spring-boot-3");
        }
    }

    @Test
    void checkDubboAllDependencies() throws DocumentException {
        File baseFile = getBaseFile();

        List<File> poms = new LinkedList<>();
        readPoms(baseFile, poms);

        SAXReader reader = new SAXReader();

        List<String> artifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        Assertions.assertEquals(poms.size(), artifactIds.size());

        List<String> deployedArtifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .filter(doc -> !Objects.equals("pom", doc.elementText("packaging")))
                .filter(doc -> Objects.isNull(doc.element("properties"))
                        || (!Objects.equals("true", doc.element("properties").elementText("skip_maven_deploy"))
                                && !Objects.equals(
                                        "true", doc.element("properties").elementText("maven.deploy.skip"))))
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        String dubboAllPath = "dubbo-distribution" + File.separator + "dubbo-all" + File.separator + "pom.xml";
        Document dubboAll = reader.read(new File(getBaseFile(), dubboAllPath));
        List<String> artifactIdsInDubboAll =
                dubboAll.getRootElement().element("dependencies").elements("dependency").stream()
                        .map(ele -> ele.elementText("artifactId"))
                        .collect(Collectors.toList());

        List<String> expectedArtifactIds = new LinkedList<>(deployedArtifactIds);
        expectedArtifactIds.removeAll(artifactIdsInDubboAll);
        expectedArtifactIds.removeIf(artifactId -> ignoredModules.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));
        expectedArtifactIds.removeIf(artifactId -> ignoredModulesInDubboAll.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));

        Assertions.assertTrue(
                expectedArtifactIds.isEmpty(),
                "Newly created modules must be added to dubbo-all(dubbo-distribution" + File.separator + "dubbo-all"
                        + File.separator + "pom.xml). Found modules: " + expectedArtifactIds);

        List<String> unexpectedArtifactIds = new LinkedList<>(artifactIdsInDubboAll);
        unexpectedArtifactIds.removeIf(artifactId -> !artifactIds.contains(artifactId));
        unexpectedArtifactIds.removeAll(deployedArtifactIds);
        Assertions.assertTrue(
                unexpectedArtifactIds.isEmpty(),
                "Undeploy dependencies should not be added to dubbo-all(dubbo-distribution" + File.separator
                        + "dubbo-all" + File.separator + "pom.xml). Found modules: " + unexpectedArtifactIds);

        unexpectedArtifactIds = new LinkedList<>();
        for (String artifactId : artifactIdsInDubboAll) {
            if (!artifactIds.contains(artifactId)) {
                continue;
            }
            if (ignoredModules.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                unexpectedArtifactIds.add(artifactId);
            }
            if (ignoredModulesInDubboAll.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                unexpectedArtifactIds.add(artifactId);
            }
        }
        Assertions.assertTrue(
                unexpectedArtifactIds.isEmpty(),
                "Unexpected dependencies should not be added to dubbo-all(dubbo-distribution" + File.separator
                        + "dubbo-all" + File.separator + "pom.xml). Found modules: " + unexpectedArtifactIds);
    }

    @Test
    void checkDubboAllShade() throws DocumentException {
        File baseFile = getBaseFile();

        List<File> poms = new LinkedList<>();
        readPoms(baseFile, poms);

        SAXReader reader = new SAXReader();

        List<String> artifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        Assertions.assertEquals(poms.size(), artifactIds.size());

        List<String> deployedArtifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .filter(doc -> Objects.isNull(doc.element("properties"))
                        || (!Objects.equals("true", doc.element("properties").elementText("skip_maven_deploy"))
                                && !Objects.equals(
                                        "true", doc.element("properties").elementText("maven.deploy.skip"))))
                .filter(doc -> !Objects.equals("pom", doc.elementText("packaging")))
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        String dubboAllPath = "dubbo-distribution" + File.separator + "dubbo-all" + File.separator + "pom.xml";
        Document dubboAll = reader.read(new File(getBaseFile(), dubboAllPath));
        List<String> artifactIdsInDubboAll =
                dubboAll.getRootElement().element("build").element("plugins").elements("plugin").stream()
                        .filter(ele -> ele.elementText("artifactId").equals("maven-shade-plugin"))
                        .map(ele -> ele.element("executions"))
                        .map(ele -> ele.elements("execution"))
                        .flatMap(Collection::stream)
                        .filter(ele -> ele.elementText("phase").equals("package"))
                        .map(ele -> ele.element("configuration"))
                        .map(ele -> ele.element("artifactSet"))
                        .map(ele -> ele.element("includes"))
                        .map(ele -> ele.elements("include"))
                        .flatMap(Collection::stream)
                        .map(Element::getText)
                        .filter(artifactId -> artifactId.startsWith("org.apache.dubbo:"))
                        .map(artifactId -> artifactId.substring("org.apache.dubbo:".length()))
                        .collect(Collectors.toList());

        List<String> expectedArtifactIds = new LinkedList<>(deployedArtifactIds);
        expectedArtifactIds.removeAll(artifactIdsInDubboAll);
        expectedArtifactIds.removeIf(artifactId -> ignoredModules.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));
        expectedArtifactIds.removeIf(artifactId -> ignoredModulesInDubboAll.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));

        Assertions.assertTrue(
                expectedArtifactIds.isEmpty(),
                "Newly created modules must be added to dubbo-all (dubbo-distribution" + File.separator + "dubbo-all"
                        + File.separator + "pom.xml in shade plugin). Found modules: " + expectedArtifactIds);

        List<String> unexpectedArtifactIds = new LinkedList<>(artifactIdsInDubboAll);
        unexpectedArtifactIds.removeIf(artifactId -> !artifactIds.contains(artifactId));
        unexpectedArtifactIds.removeAll(deployedArtifactIds);
        Assertions.assertTrue(
                unexpectedArtifactIds.isEmpty(),
                "Undeploy dependencies should not be added to dubbo-all (dubbo-distribution" + File.separator
                        + "dubbo-all" + File.separator + "pom.xml in shade plugin). Found modules: "
                        + unexpectedArtifactIds);

        unexpectedArtifactIds = new LinkedList<>();
        for (String artifactId : artifactIdsInDubboAll) {
            if (!artifactIds.contains(artifactId)) {
                continue;
            }
            if (ignoredModulesInDubboAllShade.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                continue;
            }
            if (ignoredModules.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                unexpectedArtifactIds.add(artifactId);
            }
            if (ignoredModulesInDubboAll.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                unexpectedArtifactIds.add(artifactId);
            }
        }
        Assertions.assertTrue(
                unexpectedArtifactIds.isEmpty(),
                "Unexpected dependencies should not be added to dubbo-all (dubbo-distribution" + File.separator
                        + "dubbo-all" + File.separator + "pom.xml in shade plugin). Found modules: "
                        + unexpectedArtifactIds);
    }

    @Test
    void checkDubboAllNettyShade() throws DocumentException {
        File baseFile = getBaseFile();

        List<File> poms = new LinkedList<>();
        readPoms(baseFile, poms);

        SAXReader reader = new SAXReader();

        List<String> artifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        Assertions.assertEquals(poms.size(), artifactIds.size());

        List<String> deployedArtifactIds = poms.stream()
                .map(f -> {
                    try {
                        return reader.read(f);
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Document::getRootElement)
                .filter(doc -> Objects.isNull(doc.element("properties"))
                        || (!Objects.equals("true", doc.element("properties").elementText("skip_maven_deploy"))
                                && !Objects.equals(
                                        "true", doc.element("properties").elementText("maven.deploy.skip"))))
                .filter(doc -> !Objects.equals("pom", doc.elementText("packaging")))
                .map(doc -> doc.elementText("artifactId"))
                .sorted()
                .collect(Collectors.toList());

        String dubboAllPath = "dubbo-distribution" + File.separator + "dubbo-all-shaded" + File.separator + "pom.xml";
        Document dubboAll = reader.read(new File(getBaseFile(), dubboAllPath));
        List<String> artifactIdsInDubboAll =
                dubboAll.getRootElement().element("build").element("plugins").elements("plugin").stream()
                        .filter(ele -> ele.elementText("artifactId").equals("maven-shade-plugin"))
                        .map(ele -> ele.element("executions"))
                        .map(ele -> ele.elements("execution"))
                        .flatMap(Collection::stream)
                        .filter(ele -> ele.elementText("phase").equals("package"))
                        .map(ele -> ele.element("configuration"))
                        .map(ele -> ele.element("artifactSet"))
                        .map(ele -> ele.element("includes"))
                        .map(ele -> ele.elements("include"))
                        .flatMap(Collection::stream)
                        .map(Element::getText)
                        .filter(artifactId -> artifactId.startsWith("org.apache.dubbo:"))
                        .map(artifactId -> artifactId.substring("org.apache.dubbo:".length()))
                        .collect(Collectors.toList());

        List<String> expectedArtifactIds = new LinkedList<>(deployedArtifactIds);
        expectedArtifactIds.removeAll(artifactIdsInDubboAll);
        expectedArtifactIds.removeIf(artifactId -> ignoredModules.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));
        expectedArtifactIds.removeIf(artifactId -> ignoredModulesInDubboAll.stream()
                .anyMatch(pattern -> pattern.matcher(artifactId).matches()));

        Assertions.assertTrue(
                expectedArtifactIds.isEmpty(),
                "Newly created modules must be added to dubbo-all-shaded (dubbo-distribution" + File.separator
                        + "dubbo-all-shaded" + File.separator + "pom.xml in shade plugin). Found modules: "
                        + expectedArtifactIds);

        List<String> unexpectedArtifactIds = new LinkedList<>(artifactIdsInDubboAll);
        unexpectedArtifactIds.removeIf(artifactId -> !artifactIds.contains(artifactId));
        unexpectedArtifactIds.removeAll(deployedArtifactIds);
        Assertions.assertTrue(
                unexpectedArtifactIds.isEmpty(),
                "Undeploy dependencies should not be added to dubbo-all-shaded (dubbo-distribution" + File.separator
                        + "dubbo-all-shaded" + File.separator + "pom.xml in shade plugin). Found modules: "
                        + unexpectedArtifactIds);

        unexpectedArtifactIds = new LinkedList<>();
        for (String artifactId : artifactIdsInDubboAll) {
            if (!artifactIds.contains(artifactId)) {
                continue;
            }
            if (ignoredModulesInDubboAllShade.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                continue;
            }
            if (ignoredModules.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                unexpectedArtifactIds.add(artifactId);
            }
            if (ignoredModulesInDubboAll.stream()
                    .anyMatch(pattern -> pattern.matcher(artifactId).matches())) {
                unexpectedArtifactIds.add(artifactId);
            }
        }
        Assertions.assertTrue(
                unexpectedArtifactIds.isEmpty(),
                "Unexpected dependencies should not be added to dubbo-all-shaded (dubbo-distribution" + File.separator
                        + "dubbo-all-shaded" + File.separator + "pom.xml in shade plugin). Found modules: "
                        + unexpectedArtifactIds);
    }

    @Test
    void checkDubboTransform() throws DocumentException {
        File baseFile = getBaseFile();
        List<String> spis = new LinkedList<>();
        readSPI(baseFile, spis);

        String dubboAllPath = "dubbo-distribution" + File.separator + "dubbo-all" + File.separator + "pom.xml";
        String dubboAllShadedPath =
                "dubbo-distribution" + File.separator + "dubbo-all-shaded" + File.separator + "pom.xml";
        String dubboCoreSPIPath = "dubbo-distribution" + File.separator + "dubbo-core-spi" + File.separator + "pom.xml";

        SAXReader reader = new SAXReader();
        Document dubboAll = reader.read(new File(baseFile, dubboAllPath));
        Document dubboAllShaded = reader.read(new File(baseFile, dubboAllShadedPath));
        Document dubboCoreSPI = reader.read(new File(baseFile, dubboCoreSPIPath));

        List<String> transformsInDubboAll =
                dubboAll.getRootElement().element("build").element("plugins").elements("plugin").stream()
                        .filter(ele -> ele.elementText("artifactId").equals("maven-shade-plugin"))
                        .map(ele -> ele.element("executions"))
                        .map(ele -> ele.elements("execution"))
                        .flatMap(Collection::stream)
                        .filter(ele -> ele.elementText("phase").equals("package"))
                        .map(ele -> ele.element("configuration"))
                        .map(ele -> ele.element("transformers"))
                        .map(ele -> ele.elements("transformer"))
                        .flatMap(Collection::stream)
                        .map(ele -> ele.elementText("resource"))
                        .map(String::trim)
                        .map(resource -> resource.substring(resource.lastIndexOf("/") + 1))
                        .collect(Collectors.toList());

        List<String> transformsInDubboAllShaded =
                dubboAllShaded.getRootElement().element("build").element("plugins").elements("plugin").stream()
                        .filter(ele -> ele.elementText("artifactId").equals("maven-shade-plugin"))
                        .map(ele -> ele.element("executions"))
                        .map(ele -> ele.elements("execution"))
                        .flatMap(Collection::stream)
                        .filter(ele -> ele.elementText("phase").equals("package"))
                        .map(ele -> ele.element("configuration"))
                        .map(ele -> ele.element("transformers"))
                        .map(ele -> ele.elements("transformer"))
                        .flatMap(Collection::stream)
                        .map(ele -> ele.elementText("resource"))
                        .map(String::trim)
                        .map(resource -> resource.substring(resource.lastIndexOf("/") + 1))
                        .collect(Collectors.toList());

        List<String> transformsInDubboCoreSPI =
                dubboCoreSPI.getRootElement().element("build").element("plugins").elements("plugin").stream()
                        .filter(ele -> ele.elementText("artifactId").equals("maven-shade-plugin"))
                        .map(ele -> ele.element("executions"))
                        .map(ele -> ele.elements("execution"))
                        .flatMap(Collection::stream)
                        .filter(ele -> ele.elementText("phase").equals("package"))
                        .map(ele -> ele.element("configuration"))
                        .map(ele -> ele.element("transformers"))
                        .map(ele -> ele.elements("transformer"))
                        .flatMap(Collection::stream)
                        .map(ele -> ele.elementText("resource"))
                        .map(String::trim)
                        .map(resource -> resource.substring(resource.lastIndexOf("/") + 1))
                        .collect(Collectors.toList());

        List<String> expectedSpis = new LinkedList<>(spis);
        expectedSpis.removeAll(transformsInDubboAll);
        Assertions.assertTrue(
                expectedSpis.isEmpty(),
                "Newly created SPI interface must be added to dubbo-all(dubbo-distribution" + File.separator
                        + "dubbo-all" + File.separator + "pom.xml in shade plugin) to being transformed. Found spis: "
                        + expectedSpis);

        List<String> unexpectedSpis = new LinkedList<>(transformsInDubboAll);
        unexpectedSpis.removeAll(spis);
        Assertions.assertTrue(
                unexpectedSpis.isEmpty(),
                "Class without `@SPI` declaration should not be added to dubbo-all(dubbo-distribution" + File.separator
                        + "dubbo-all" + File.separator + "pom.xml in shade plugin) to being transformed. Found spis: "
                        + unexpectedSpis);

        expectedSpis = new LinkedList<>(spis);
        expectedSpis.removeAll(transformsInDubboAllShaded);
        Assertions.assertTrue(
                expectedSpis.isEmpty(),
                "Newly created SPI interface must be added to dubbo-all-shaded(dubbo-distribution" + File.separator
                        + "dubbo-all-shaded" + File.separator
                        + "pom.xml in shade plugin) to being transformed. Found spis: "
                        + expectedSpis);

        unexpectedSpis = new LinkedList<>(transformsInDubboAllShaded);
        unexpectedSpis.removeAll(spis);
        Assertions.assertTrue(
                unexpectedSpis.isEmpty(),
                "Class without `@SPI` declaration should not be added to dubbo-all-shaded(dubbo-distribution"
                        + File.separator
                        + "dubbo-all-shaded" + File.separator
                        + "pom.xml in shade plugin) to being transformed. Found spis: "
                        + unexpectedSpis);

        expectedSpis = new LinkedList<>(spis);
        expectedSpis.removeAll(transformsInDubboCoreSPI);
        Assertions.assertTrue(
                expectedSpis.isEmpty(),
                "Newly created SPI interface must be added to dubbo-core-spi(dubbo-distribution" + File.separator
                        + "dubbo-core-spi" + File.separator
                        + "pom.xml in shade plugin) to being transformed. Found spis: "
                        + expectedSpis);

        unexpectedSpis = new LinkedList<>(transformsInDubboCoreSPI);
        unexpectedSpis.removeAll(spis);
        Assertions.assertTrue(
                unexpectedSpis.isEmpty(),
                "Class without `@SPI` declaration should not be added to dubbo-core-spi(dubbo-distribution"
                        + File.separator
                        + "dubbo-core-spi" + File.separator
                        + "pom.xml in shade plugin) to being transformed. Found spis: "
                        + unexpectedSpis);
    }

    @Test
    void checkSpiFiles() {
        File baseFile = getBaseFile();
        List<String> spis = new LinkedList<>();
        readSPI(baseFile, spis);

        Map<File, String> spiResources = new HashMap<>();
        readSPIResource(baseFile, spiResources);
        Map<File, String> copyOfSpis = new HashMap<>(spiResources);
        copyOfSpis.entrySet().removeIf(entry -> spis.contains(entry.getValue()));
        Assertions.assertTrue(
                copyOfSpis.isEmpty(),
                "Newly created spi profiles must have a valid class declared with `@SPI`. Found spi profiles: "
                        + copyOfSpis.keySet());

        List<File> unexpectedSpis = new LinkedList<>();
        readSPIUnexpectedResource(baseFile, unexpectedSpis);
        String commonSpiPath = "dubbo-common" + File.separator + "src" + File.separator + "main" + File.separator
                + "resources" + File.separator + "META-INF" + File.separator + "services" + File.separator;
        unexpectedSpis.removeIf(file -> {
            String path = file.getAbsolutePath();
            return path.contains(commonSpiPath + "org.apache.dubbo.common.extension.LoadingStrategy")
                    || path.contains(commonSpiPath + "org.apache.dubbo.common.json.JsonUtil");
        });
        Assertions.assertTrue(
                unexpectedSpis.isEmpty(),
                "Dubbo native provided spi profiles must filed in `META-INF" + File.separator + "dubbo" + File.separator
                        + "internal`. Please move to proper folder . Found spis: " + unexpectedSpis);
    }

    private static File getBaseFile() {
        File baseFile = new File(new File("").getAbsolutePath());
        while (baseFile != null) {
            if (new File(baseFile, ".asf.yaml").exists()) {
                break;
            }
            baseFile = baseFile.getParentFile();
        }
        Assertions.assertNotNull(baseFile, "Can not find base dir");

        return baseFile;
    }

    public void readPoms(File path, List<File> poms) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    readPoms(file, poms);
                }
            }
        } else if (path.isFile()) {
            if (path.getAbsolutePath().contains("target")) {
                return;
            }
            if (path.getName().equals("pom.xml")) {
                poms.add(path);
            }
        }
    }

    public void readSPI(File path, List<String> spis) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    readSPI(file, spis);
                }
            }
        } else if (path.isFile()) {
            if (path.getAbsolutePath().contains("target")) {
                return;
            }
            if (path.getAbsolutePath().contains("src" + File.separator + "main" + File.separator + "java")) {
                String content;
                try {
                    content = FileUtils.readFileToString(path, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (content != null && content.contains("@SPI")) {
                    String absolutePath = path.getAbsolutePath();
                    absolutePath = absolutePath.substring(absolutePath.lastIndexOf(
                                    "src" + File.separator + "main" + File.separator + "java" + File.separator)
                            + ("src" + File.separator + "main" + File.separator + "java" + File.separator).length());
                    absolutePath = absolutePath.substring(0, absolutePath.lastIndexOf(".java"));
                    absolutePath = absolutePath.replaceAll(Matcher.quoteReplacement(File.separator), ".");
                    spis.add(absolutePath);
                }
            }
        }
    }

    public void readSPIResource(File path, Map<File, String> spis) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    readSPIResource(file, spis);
                }
            }
        } else if (path.isFile()) {
            if (path.getAbsolutePath().contains("target")) {
                return;
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF" + File.separator + "dubbo" + File.separator + "internal" + File.separator)) {
                String absolutePath = path.getAbsolutePath();
                absolutePath = absolutePath.substring(absolutePath.lastIndexOf("src" + File.separator + "main"
                                + File.separator + "resources" + File.separator + "META-INF" + File.separator + "dubbo"
                                + File.separator + "internal" + File.separator)
                        + ("src" + File.separator + "main" + File.separator + "resources" + File.separator + "META-INF"
                                        + File.separator + "dubbo" + File.separator + "internal" + File.separator)
                                .length());
                absolutePath = absolutePath.replaceAll(Matcher.quoteReplacement(File.separator), ".");
                spis.put(path, absolutePath);
            }
        }
    }

    public void readSPIUnexpectedResource(File path, List<File> spis) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    readSPIUnexpectedResource(file, spis);
                }
            }
        } else if (path.isFile()) {
            if (path.getAbsolutePath().contains("target")) {
                return;
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF" + File.separator + "dubbo" + File.separator + "org.apache.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF" + File.separator + "dubbo" + File.separator + "com.alibaba.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF" + File.separator + "services" + File.separator + "org.apache.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF" + File.separator + "services" + File.separator + "com.alibaba.dubbo")) {
                spis.add(path);
            }

            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF.dubbo" + File.separator + "org.apache.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF.dubbo" + File.separator + "com.alibaba.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF.services" + File.separator + "org.apache.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF.services" + File.separator + "com.alibaba.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF.dubbo.internal" + File.separator + "org.apache.dubbo")) {
                spis.add(path);
            }
            if (path.getAbsolutePath()
                    .contains("src" + File.separator + "main" + File.separator + "resources" + File.separator
                            + "META-INF.dubbo.internal" + File.separator + "com.alibaba.dubbo")) {
                spis.add(path);
            }
        }
    }

    private boolean hasProfileDependency(Element rootElement, String profileId, String artifactId) {
        Element profiles = rootElement.element("profiles");
        if (profiles == null) {
            return false;
        }
        return profiles.elements("profile").stream()
                .filter(profile -> Objects.equals(profileId, profile.elementText("id")))
                .map(profile -> profile.element("dependencies"))
                .filter(Objects::nonNull)
                .map(dependencies -> dependencies.elements("dependency"))
                .flatMap(Collection::stream)
                .anyMatch(dependency -> Objects.equals(artifactId, dependency.elementText("artifactId")));
    }

    private boolean hasProfileProperty(Element rootElement, String profileId, String propertyName, String value) {
        Element profiles = rootElement.element("profiles");
        if (profiles == null) {
            return false;
        }
        return profiles.elements("profile").stream()
                .filter(profile -> Objects.equals(profileId, profile.elementText("id")))
                .map(profile -> profile.element("properties"))
                .filter(Objects::nonNull)
                .anyMatch(properties -> Objects.equals(value, properties.elementText(propertyName)));
    }

    private boolean hasProfileSource(Element rootElement, String profileId, String source) {
        Element profiles = rootElement.element("profiles");
        if (profiles == null) {
            return false;
        }
        return profiles.elements("profile").stream()
                .filter(profile -> Objects.equals(profileId, profile.elementText("id")))
                .map(profile -> profile.element("build"))
                .filter(Objects::nonNull)
                .map(build -> build.element("plugins"))
                .filter(Objects::nonNull)
                .map(plugins -> plugins.elements("plugin"))
                .flatMap(Collection::stream)
                .map(plugin -> plugin.element("executions"))
                .filter(Objects::nonNull)
                .map(executions -> executions.elements("execution"))
                .flatMap(Collection::stream)
                .map(execution -> execution.element("configuration"))
                .filter(Objects::nonNull)
                .map(configuration -> configuration.element("sources"))
                .filter(Objects::nonNull)
                .map(sources -> sources.elements("source"))
                .flatMap(Collection::stream)
                .anyMatch(sourceElement -> Objects.equals(source, sourceElement.getTextTrim()));
    }

    private boolean hasDependency(Element rootElement, String artifactId) {
        Element dependencies = rootElement.element("dependencies");
        if (dependencies == null) {
            return false;
        }
        return dependencies.elements("dependency").stream()
                .anyMatch(dependency -> Objects.equals(artifactId, dependency.elementText("artifactId")));
    }

    private boolean hasManagedDependency(Element rootElement, String groupId, String artifactId, String version) {
        Element dependencyManagement = rootElement.element("dependencyManagement");
        if (dependencyManagement == null) {
            return false;
        }
        Element dependencies = dependencyManagement.element("dependencies");
        if (dependencies == null) {
            return false;
        }
        return dependencies.elements("dependency").stream()
                .anyMatch(dependency -> Objects.equals(groupId, dependency.elementText("groupId"))
                        && Objects.equals(artifactId, dependency.elementText("artifactId"))
                        && Objects.equals(version, dependency.elementText("version")));
    }

    private boolean hasProfileManagedDependency(
            Element rootElement, String profileId, String groupId, String artifactId, String version) {
        Element profiles = rootElement.element("profiles");
        if (profiles == null) {
            return false;
        }
        return profiles.elements("profile").stream()
                .filter(profile -> Objects.equals(profileId, profile.elementText("id")))
                .map(profile -> profile.element("dependencyManagement"))
                .filter(Objects::nonNull)
                .map(dependencyManagement -> dependencyManagement.element("dependencies"))
                .filter(Objects::nonNull)
                .map(dependencies -> dependencies.elements("dependency"))
                .flatMap(Collection::stream)
                .anyMatch(dependency -> Objects.equals(groupId, dependency.elementText("groupId"))
                        && Objects.equals(artifactId, dependency.elementText("artifactId"))
                        && Objects.equals(version, dependency.elementText("version")));
    }

    private boolean hasResourceEntry(File baseFile, String resourcePath, String resourceEntry) throws IOException {
        return FileUtils.readFileToString(new File(baseFile, resourcePath), StandardCharsets.UTF_8)
                .contains(resourceEntry);
    }
}
