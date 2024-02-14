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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ConjureLocalGenerateGenericTask extends ConjureLocalGenerateTask {

    private static final Pattern PATTERN =
            Pattern.compile("^(.*)-([0-9]+\\.[0-9]+\\.[0-9]+(?:-rc[0-9]+)?(?:-[1-9][0-9]*-g[a-f0-9]+)?)(?:.conjure)?.json$");

    @Override
    protected final Map<String, Supplier<Object>> requiredOptions(File irFile) {
        return resolveProductMetadata(irFile.getName());
    }

    @VisibleForTesting
    static Map<String, Supplier<Object>> resolveProductMetadata(String productName) {
        Matcher matcher = PATTERN.matcher(productName);
        if (!matcher.matches() || matcher.groupCount() != 2) {
            throw new RuntimeException(String.format("Unable to parse conjure dependency name %s", productName));
        }

        String irName = matcher.group(1);
        String irVersion = matcher.group(2);
        return ImmutableMap.of("productName", () -> irName, "productVersion", () -> irVersion);
    }
}
