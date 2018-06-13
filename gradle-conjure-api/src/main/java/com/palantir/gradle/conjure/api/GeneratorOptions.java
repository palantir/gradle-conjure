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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class GeneratorOptions implements Serializable {
    private static final long serialVersionUID = 5676541916502995769L;
    /** Keys must be defined in camelCase. */
    private static Predicate<String> expectedKeyPattern = Pattern.compile("[a-z][a-zA-Z0-9]*").asPredicate();
    private final Map<String, Object> storage;

    public GeneratorOptions() {
        storage = new LinkedHashMap<>();
    }

    public GeneratorOptions(GeneratorOptions options) {
        this.storage = options.storage;
    }

    public void setProperty(String name, Object newValue) {
        if (name.equals("properties")) {
            throw new RuntimeException("Can't override the 'properties' property");
        } else {
            this.set(name, newValue);
        }
    }

    public Map<String, Object> getProperties() {
        return ImmutableMap.copyOf(this.storage);
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

    private void set(String name, Object value) {
        Preconditions.checkNotNull(name, "Key cannot be null");
        Preconditions.checkArgument(expectedKeyPattern.test(name), "Key must be camelCase: %s", name);
        Preconditions.checkNotNull(value, "Property '%s': value cannot be null", name);
        this.storage.put(name, value);
    }
}
