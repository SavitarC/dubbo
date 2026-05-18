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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SpringBootConditionTests {

    private final boolean springBootBefore4 = SpringBootBefore4Condition.IS_SPRING_BOOT_BEFORE_4;
    private final boolean springBoot4 = SpringBoot4Condition.IS_SPRING_BOOT_4;

    @AfterEach
    void resetConditions() {
        SpringBootBefore4Condition.IS_SPRING_BOOT_BEFORE_4 = springBootBefore4;
        SpringBoot4Condition.IS_SPRING_BOOT_4 = springBoot4;
    }

    @Test
    void shouldMatchOnlyBefore4ConditionBeforeSpringBoot4() {
        SpringBootBefore4Condition.IS_SPRING_BOOT_BEFORE_4 = true;
        SpringBoot4Condition.IS_SPRING_BOOT_4 = false;

        Assertions.assertTrue(new SpringBootBefore4Condition().matches(null, null));
        Assertions.assertFalse(new SpringBoot4Condition().matches(null, null));
    }

    @Test
    void shouldMatchOnlySpringBoot4ConditionOnSpringBoot4() {
        SpringBootBefore4Condition.IS_SPRING_BOOT_BEFORE_4 = false;
        SpringBoot4Condition.IS_SPRING_BOOT_4 = true;

        Assertions.assertFalse(new SpringBootBefore4Condition().matches(null, null));
        Assertions.assertTrue(new SpringBoot4Condition().matches(null, null));
    }

    @Test
    void shouldParseSpringBootVersionsNullSafely() {
        Assertions.assertTrue(SpringBootVersionUtils.getMajorVersion(null) < 0);
        Assertions.assertTrue(SpringBootVersionUtils.getMajorVersion("") < 0);
        Assertions.assertEquals(3, SpringBootVersionUtils.getMajorVersion("3.5.14"));
        Assertions.assertEquals(4, SpringBootVersionUtils.getMajorVersion("4.0.6"));

        Assertions.assertFalse(SpringBootVersionUtils.isSpringBoot4("3.5.14"));
        Assertions.assertTrue(SpringBootVersionUtils.isBeforeSpringBoot4("3.5.14"));
        Assertions.assertTrue(SpringBootVersionUtils.isSpringBoot4("4.0.6"));
        Assertions.assertFalse(SpringBootVersionUtils.isBeforeSpringBoot4("4.0.6"));
    }

    @Test
    void shouldEvaluateConditionsWhenSpringBootVersionIsUnavailable() {
        Assertions.assertDoesNotThrow(() -> SpringBootVersionUtils.isSpringBoot4(null));
        Assertions.assertDoesNotThrow(() -> SpringBootVersionUtils.isBeforeSpringBoot4(null));
    }
}
