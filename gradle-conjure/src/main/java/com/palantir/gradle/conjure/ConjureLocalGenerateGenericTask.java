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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Map;
import java.util.function.Supplier;

public abstract class ConjureLocalGenerateGenericTask extends ConjureLocalGenerateTask {

    @Override
    protected final Map<String, Supplier<Object>> requiredOptions(File irFile) {
        return resolveProductMetadata(irFile.getName());
    }

    @VisibleForTesting
    static Map<String, Supplier<Object>> resolveProductMetadata(String productName) {
        ProductNameAndVersion nameAndVersion = parseProductNameAndVersion(productName);
        String irName = nameAndVersion.name();
        String irVersion = nameAndVersion.version().getValue();

        return ImmutableMap.of("productName", () -> irName, "productVersion", () -> irVersion);
    }
}
