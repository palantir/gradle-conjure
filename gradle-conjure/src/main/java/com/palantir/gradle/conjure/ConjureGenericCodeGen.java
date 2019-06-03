/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.util.GUtil;

/** Provides helper methods for working with RFC002-compliant Conjure code generators. */
final class ConjureGenericCodeGen {

    private ConjureGenericCodeGen() {}

    private static final String CONJURE_GENERATOR_DEP_PREFIX = "conjure-";

    static ExtractExecutableTask createExtractExecutableTask(Project project, Configuration configuration) {
        // We create a lazy filtered FileCollection to avoid using afterEvaluate.
        FileCollection matchingGeneratorDeps = configuration.fileCollection(
                dep -> dep.getName().equals(CONJURE_GENERATOR_DEP_PREFIX + project.getName()));

        return ExtractExecutableTask.createExtractTask(
                project.getParent(),  // TODO(rfink): why parent, why not in project itself?
                GUtil.toLowerCamelCase("extractConjure " + project.getName()),
                matchingGeneratorDeps,
                new File(project.getBuildDir(), "generator"),
                String.format("conjure-%s", project.getName()));
    }

    // Validates that each subproject has a corresponding generator.
    // We do this in afterEvaluate to ensure the configuration is populated.
    static void validateProjectNamesMapToGeneratorNames(
            Project project, Configuration configuration, Set<String> exemptProjects) {
        project.afterEvaluate(p -> {
            Map<String, Dependency> generators = configuration
                    .getAllDependencies()
                    .stream()
                    .collect(Collectors.toMap(dependency -> {
                        Preconditions.checkState(dependency.getName().startsWith(CONJURE_GENERATOR_DEP_PREFIX),
                                "Generators should start with '%s' according to conjure RFC 002, "
                                        + "but found name: '%s' (%s)",
                                CONJURE_GENERATOR_DEP_PREFIX, dependency.getName(), dependency);
                        return dependency.getName().substring(CONJURE_GENERATOR_DEP_PREFIX.length());
                    }, Function.identity()));

            project.getChildProjects().values().stream()
                    .filter(subProject -> !exemptProjects.contains(subProject.getName()))
                    .forEach(subProject -> {
                        if (!generators.containsKey(subProject.getName())) {
                            throw new RuntimeException(String.format("Discovered subproject %s without corresponding "
                                            + "generator dependency with name '%s'",
                                    subProject.getPath(), CONJURE_GENERATOR_DEP_PREFIX + subProject.getName()));
                        }
                    });
        });
    }
}
