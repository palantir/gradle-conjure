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

package com.palantir.gradle.conjure.api;

import groovy.lang.Closure;

public class ConjureExtension {

    public static final String EXTENSION_NAME = "conjure";

    private final GeneratorOptions typescriptConfiguration = new GeneratorOptions();
    private final GeneratorOptions javaConfiguration = new GeneratorOptions();

    /**
     * @deprecated use the {@link #java(Closure)} method to configure feature flags by setting {@code feature = true}.
     */
    @Deprecated
    public final void javaFeatureFlag(String feature) {
        javaConfiguration.setProperty(feature, true);
    }

    public final void typescript(Closure closure) {
        closure.setDelegate(typescriptConfiguration);
        closure.call();
    }

    public final void java(Closure closure) {
        closure.setDelegate(javaConfiguration);
        closure.call();
    }

    public final GeneratorOptions getTypeScriptExtension() {
        return typescriptConfiguration;
    }

    public final GeneratorOptions getJavaExtension() {
        return javaConfiguration;
    }
}
