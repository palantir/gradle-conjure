/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.conjure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;

public class ConjureLocalGenerateGenericTaskTest {

    @Test
    public void correctlyParsesProductMetadata() {
        Map<String, Supplier<Object>> productMetadata;

        productMetadata = ConjureLocalGenerateGenericTask.resolveProductMetadata("foo-1.0.0.json");
        assertThat(productMetadata.get("productName").get()).isEqualTo("foo");
        assertThat(productMetadata.get("productVersion").get()).isEqualTo("1.0.0");

        productMetadata = ConjureLocalGenerateGenericTask.resolveProductMetadata("foo-baz-1.0.0.json");
        assertThat(productMetadata.get("productName").get()).isEqualTo("foo-baz");
        assertThat(productMetadata.get("productVersion").get()).isEqualTo("1.0.0");

        productMetadata = ConjureLocalGenerateGenericTask.resolveProductMetadata("foo-1.0.0.conjure.json");
        assertThat(productMetadata.get("productName").get()).isEqualTo("foo");
        assertThat(productMetadata.get("productVersion").get()).isEqualTo("1.0.0");

        productMetadata = ConjureLocalGenerateGenericTask.resolveProductMetadata("foo-1.0.0-rc1.conjure.json");
        assertThat(productMetadata.get("productName").get()).isEqualTo("foo");
        assertThat(productMetadata.get("productVersion").get()).isEqualTo("1.0.0-rc1");

        productMetadata = ConjureLocalGenerateGenericTask.resolveProductMetadata("foo-1.0.0-gabcd.conjure.json");
        assertThat(productMetadata.get("productName").get()).isEqualTo("foo");
        assertThat(productMetadata.get("productVersion").get()).isEqualTo("1.0.0-gabcd");

        productMetadata = ConjureLocalGenerateGenericTask.resolveProductMetadata("foo-1.0.0-rc1-gabcd.conjure.json");
        assertThat(productMetadata.get("productName").get()).isEqualTo("foo");
        assertThat(productMetadata.get("productVersion").get()).isEqualTo("1.0.0-rc1-gabcd");
    }

    @Test
    public void failsToParseInvalidNames() {
        ImmutableList.of("invalid-name.json", "invalid-version-1.x.0.json", "invalid-structure-1.2.0.tgz")
                .forEach(testCase ->
                        assertThatThrownBy(() -> ConjureLocalGenerateGenericTask.resolveProductMetadata(testCase))
                                .hasMessageContaining(testCase));
    }
}
