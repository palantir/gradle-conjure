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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.spec.ConjureDefinition;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileTree;

public final class ConjureDefinitions {

    private ConjureDefinitions() {}

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT);

    private static List<ConjureDefinition> getCompileIrs(FileTree source) {
        return source.getFiles().stream().map(ConjureDefinitions::parseIr).collect(Collectors.toList());
    }

    private static List<ConjureDefinition> getConfiguredIrs(Configuration configuration) {
        return configuration.getResolvedConfiguration().getResolvedArtifacts().stream()
                .map(ir -> parseIr(ir.getFile()))
                .collect(Collectors.toList());
    }

    public static List<ConjureDefinition> getAllDefinitions(Configuration configuration, FileTree source) {
        ImmutableList.Builder<ConjureDefinition> definitionBuilder = ImmutableList.builder();
        definitionBuilder.addAll(getConfiguredIrs(configuration)).addAll(getCompileIrs(source));
        return definitionBuilder.build();
    }

    private static ConjureDefinition parseIr(File file) {
        try {
            return OBJECT_MAPPER.readValue(file, ConjureDefinition.class);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to deserialize the definition given the IR file: " + file.getName());
        }
    }
}
