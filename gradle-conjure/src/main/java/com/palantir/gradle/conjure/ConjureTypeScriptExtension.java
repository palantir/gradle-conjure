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

import java.util.Optional;

public class ConjureTypeScriptExtension {
    private Optional<String> packageName = Optional.empty();
    private Optional<String> version = Optional.empty();
    private String moduleType = "es2015";

    public final void packageName(String newPackageName) {
        this.packageName = Optional.of(newPackageName);
    }

    public final void version(String specifiedVersion) {
        this.version = Optional.of(specifiedVersion);
    }

    public final void moduleType(String specifiedModuleType) {
        this.moduleType = specifiedModuleType;
    }

    public final Optional<String> getPackageName() {
        return packageName;
    }

    public final Optional<String> getVersion() {
        return version;
    }

    public final String getModuleType() {
        return moduleType;
    }
}
