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
import groovy.lang.DelegatesTo;
import java.util.HashMap;
import java.util.Map;

public class ConjureExtension {

    public static final String EXTENSION_NAME = "conjure";

    private final GeneratorOptions typescriptOptions = new GeneratorOptions();
    private final GeneratorOptions javaOptions = new GeneratorOptions();
    private final GeneratorOptions pythonOptions = new GeneratorOptions();
    private final Map<String, GeneratorOptions> genericOptions = new HashMap<>();

    public final void typescript(@DelegatesTo(GeneratorOptions.class) Closure<GeneratorOptions> closure) {
        closure.setDelegate(typescriptOptions);
        closure.call();
    }

    public final void java(@DelegatesTo(GeneratorOptions.class) Closure<GeneratorOptions> closure) {
        closure.setDelegate(javaOptions);
        closure.call();
    }

    public final void python(@DelegatesTo(GeneratorOptions.class) Closure<GeneratorOptions> closure) {
        closure.setDelegate(pythonOptions);
        closure.call();
    }

    public final void options(
            String generator, @DelegatesTo(GeneratorOptions.class) Closure<GeneratorOptions> closure) {
        closure.setDelegate(getGenericOptions(generator));
        closure.call();
    }

    public final GeneratorOptions getTypescript() {
        return typescriptOptions;
    }

    public final GeneratorOptions getJava() {
        return javaOptions;
    }

    public final GeneratorOptions getPython() {
        return pythonOptions;
    }

    public final GeneratorOptions getGenericOptions(String generator) {
        return genericOptions.computeIfAbsent(generator, g -> new GeneratorOptions());
    }
}
