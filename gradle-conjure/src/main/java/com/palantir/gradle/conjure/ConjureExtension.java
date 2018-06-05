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

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;

public class ConjureExtension {
    private final Set<String> javaFeatureFlags = Sets.newHashSet();
    private Optional<String> typeScriptPackageName = Optional.empty();
    private Optional<String> typeScriptVersion = Optional.empty();

    public final void javaFeatureFlag(String feature) {
        javaFeatureFlags.add(feature);
    }

    public final void typeScriptPackageName(String packageName) {
        this.typeScriptPackageName = Optional.of(packageName);
    }

    public final void typeScriptVersion(String packageName) {
        this.typeScriptVersion = Optional.of(packageName);
    }

    public final Optional<String> getTypeScriptPackageName() {
        return typeScriptPackageName;
    }

    public final Optional<String> getTypeScriptVersion() {
        return typeScriptVersion;
    }

    public final Set<String> getJavaFeatureFlags() {
        return javaFeatureFlags;
    }

}
