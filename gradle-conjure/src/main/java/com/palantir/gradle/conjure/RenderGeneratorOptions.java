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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RenderGeneratorOptions {
    private static final Logger log = LoggerFactory.getLogger(RenderGeneratorOptions.class);

    private RenderGeneratorOptions() { }

    /**
     * Renders a {@link GeneratorOptions} to command-line arguments.
     * @param requiredOptions a map of required options to their default values
     */
    public static List<String> toArgs(
            GeneratorOptions options, Map<String, Supplier<Object>> requiredOptions) {
        Map<String, Object> properties = options.getProperties();
        ImmutableMap.Builder<String, Object> resolvedProperties =
                ImmutableMap.<String, Object>builder().putAll(properties);
        requiredOptions.forEach((field, defaultSupplierOpt) -> {
            Object defaultValue = defaultSupplierOpt.get();
            if (!properties.containsKey(field)) {
                log.info("Field '{}' was not defined in options, falling back to default: {}",
                        field,
                        defaultValue);
                resolvedProperties.put(field, defaultValue);
            } else if (Objects.equals(defaultValue, Objects.toString(properties.get(field)))) {
                log.warn("Field '{}' was defined in options but its value is the same as the default: {}",
                        field,
                        defaultValue);
            }
        });

        return resolvedProperties.build().entrySet().stream().map(entry -> {
            Object value = entry.getValue();
            if (value == Boolean.TRUE) {
                return "--" + entry.getKey();
            }
            Preconditions.checkArgument(
                    !entry.getKey().contains("="),
                    "Conjure generator parameter '%s' cannot contain '='",
                    entry.getKey());
            String stringValue = Objects.toString(value);
            Preconditions.checkNotNull(stringValue, "Value cannot be null");
            return "--" + entry.getKey() + "=" + stringValue;
        }).collect(Collectors.toList());
    }
}
