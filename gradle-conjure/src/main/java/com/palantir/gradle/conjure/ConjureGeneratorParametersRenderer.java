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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Renders a {@link ConjureGeneratorParameters} to command-line arguments.
 */
public final class ConjureGeneratorParametersRenderer {
    public List<String> toArgs(ConjureGeneratorParameters parameters) {
        return parameters.getProperties().entrySet().stream().flatMap(entry -> {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                return value == Boolean.TRUE ? Stream.of("--" + entry.getKey()) : Stream.empty();
            }
            Preconditions.checkArgument(
                    !entry.getKey().contains("="),
                    "Conjure generator parameter '%s' cannot contain '='",
                    entry.getKey());
            String stringValue = Objects.toString(value);
            Preconditions.checkNotNull(stringValue, "Value cannot be null");
            return Stream.of("--" + entry.getKey() + "=" + stringValue);
        }).collect(Collectors.toList());
    }
}
