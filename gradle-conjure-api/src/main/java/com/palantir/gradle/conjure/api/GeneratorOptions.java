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

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class GeneratorOptions implements Serializable {
    private static final long serialVersionUID = 5676541916502995769L;

    /** Keys must be defined in camelCase. */
    private static Predicate<String> camelCase = Pattern.compile("[a-z][a-zA-Z0-9]*").asPredicate();
    private final Map<String, Object> storage;

    public GeneratorOptions() {
        this.storage = new LinkedHashMap<>();
    }

    public GeneratorOptions(GeneratorOptions options) {
        this.storage = new LinkedHashMap<>(options.getProperties());
    }

    public void setProperty(String name, Object newValue) {
        if (name.equals("properties")) {
            throw new RuntimeException("Can't override the 'properties' property");
        } else {
            this.set(name, newValue);
        }
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(this.storage);
    }

    public boolean has(String name) {
        return this.storage.containsKey(name);
    }

    public Object get(String name) {
        if (!this.has(name)) {
            throw new RuntimeException("Unknown property: " + name);
        } else {
            return this.storage.get(name);
        }
    }

    private void set(String key, Object value) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null: " + key);
        }

        if (!camelCase.test(key)) {
            throw new IllegalArgumentException("Key must be camelCase: " + key);
        }

        if (value == null) {
            throw new NullPointerException(String.format("Property '%s': value cannot be null", key));
        }

        this.storage.put(key, value);
    }

    public static GeneratorOptions addFlag(GeneratorOptions options, String flag) {
        if (options.has(flag)) {
            throw new IllegalArgumentException(
                    String.format("Passed GeneratorOptions already has flag '%s' set: %s", flag, options));
        }
        GeneratorOptions generatorOptions = new GeneratorOptions(options);
        generatorOptions.setProperty(flag, true);
        return generatorOptions;
    }

    public static GeneratorOptions addProperty(GeneratorOptions options, String propertyName, Object propertyValue) {
        GeneratorOptions generatorOptions = new GeneratorOptions(options);
        generatorOptions.setProperty(propertyName, propertyValue);
        return generatorOptions;
    }

}
