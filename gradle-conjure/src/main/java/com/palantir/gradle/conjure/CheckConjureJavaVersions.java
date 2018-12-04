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
import com.google.common.collect.MoreCollectors;
import java.util.Objects;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.tasks.TaskAction;

public class CheckConjureJavaVersions extends DefaultTask {

    public CheckConjureJavaVersions() {
        setGroup(ConjurePlugin.TASK_GROUP);
        setDescription("Ensures that conjure-java and conjure-lib versions are identical");
    }

    @TaskAction
    public final void run() {
        // 1. Figure out what version of conjure-java we resolved
        String conjureJavaVersion = findResolvedVersionOf(
                getProject(), ConjurePlugin.CONJURE_JAVA, ConjurePlugin.CONJURE_JAVA_BINARY);

        // 2. Ensure in each subproject, the version of conjure-lib in `compile` is the same.
        ConjurePlugin.JAVA_PROJECT_SUFFIXES.stream()
                .map(suffix -> getProject().findProject(getProject().getName() + suffix))
                .filter(Objects::nonNull)
                .forEach(subproj -> {
                    String conjureJavaLibVersion =
                            findResolvedVersionOf(
                                    subproj, "compile", ConjurePlugin.CONJURE_JAVA_LIB_DEP);
                    Preconditions.checkState(conjureJavaLibVersion.equals(conjureJavaVersion),
                            "conjure-java generator and lib should have the same version but found:\n"
                                    + "%s -> %s\n%s -> %s",
                            ConjurePlugin.CONJURE_JAVA_BINARY, conjureJavaVersion,
                            ConjurePlugin.CONJURE_JAVA_LIB_DEP, conjureJavaLibVersion);

                });
    }

    private static String findResolvedVersionOf(Project project, String configuration, String moduleId) {
        ResolutionResult conjureJavaResolutionResult =
                project.getConfigurations().getByName(configuration).getIncoming().getResolutionResult();
        Optional<ResolvedComponentResult> component = conjureJavaResolutionResult
                .getAllComponents()
                .stream()
                .filter(c -> c.getModuleVersion() != null
                        && moduleId.equals(c.getModuleVersion().getModule().toString()))
                .collect(MoreCollectors.toOptional());
        return component
                .orElseThrow(() ->
                        new RuntimeException(String.format("Expected to find %s in %s", moduleId, configuration)))
                .getModuleVersion()
                .getVersion();
    }
}
