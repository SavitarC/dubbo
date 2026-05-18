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

import org.springframework.boot.SpringBootVersion;
import org.springframework.util.ClassUtils;

final class SpringBootVersionUtils {

    private static final int UNKNOWN_MAJOR_VERSION = -1;

    private static final int SPRING_BOOT_4_MAJOR_VERSION = 4;

    private static final String SPRING_BOOT_4_MARKER_CLASS =
            "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingFilterBean";

    private SpringBootVersionUtils() {}

    static boolean isSpringBoot4() {
        return isSpringBoot4(SpringBootVersion.getVersion());
    }

    static boolean isSpringBoot4(String version) {
        int majorVersion = getMajorVersion(version);
        if (majorVersion != UNKNOWN_MAJOR_VERSION) {
            return majorVersion == SPRING_BOOT_4_MAJOR_VERSION;
        }
        return isSpringBoot4Present();
    }

    static boolean isBeforeSpringBoot4() {
        return isBeforeSpringBoot4(SpringBootVersion.getVersion());
    }

    static boolean isBeforeSpringBoot4(String version) {
        int majorVersion = getMajorVersion(version);
        if (majorVersion != UNKNOWN_MAJOR_VERSION) {
            return majorVersion < SPRING_BOOT_4_MAJOR_VERSION;
        }
        return !isSpringBoot4Present();
    }

    static int getMajorVersion(String version) {
        if (version == null) {
            return UNKNOWN_MAJOR_VERSION;
        }
        String trimmedVersion = version.trim();
        if (trimmedVersion.isEmpty()) {
            return UNKNOWN_MAJOR_VERSION;
        }
        int endIndex = 0;
        while (endIndex < trimmedVersion.length() && Character.isDigit(trimmedVersion.charAt(endIndex))) {
            endIndex++;
        }
        if (endIndex == 0) {
            return UNKNOWN_MAJOR_VERSION;
        }
        return Integer.parseInt(trimmedVersion.substring(0, endIndex));
    }

    private static boolean isSpringBoot4Present() {
        return ClassUtils.isPresent(SPRING_BOOT_4_MARKER_CLASS, SpringBootVersionUtils.class.getClassLoader());
    }
}
