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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileConjurePythonTask extends ConjureGeneratorTask {

    @Override
    protected final Map<String, Supplier<Object>> requiredOptions() {
        return ImmutableMap.of(
                "packageName", () -> getProject().getName()
                        .replace("-api", "").replace("-", "_"),
                "packageVersion", () -> pythonVersion(getProject().getVersion().toString()));
    }

    private String pythonVersion(String version) {
        Matcher matcher = Pattern.compile("((\\d+[.]?)+)[-_](\\d+)[-_]g(.*)").matcher(version);
        if (matcher.matches()) {
            String publicVersion = matcher.group(1);
            String distance = matcher.group(3);
            String hash = matcher.group(4);
            String pepVersion = publicVersion + "+" + distance + "." + hash;
            return pepVersion.replace("-", "_");
        }
        return version.replace("-", "_");
    }
}
