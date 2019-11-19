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
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.VersionNumber;

public class CheckConjureJavaVersions extends DefaultTask {

    public CheckConjureJavaVersions() {
        setGroup(ConjurePlugin.TASK_GROUP);
        setDescription("Ensures that conjure-lib as at least as new as conjure-java.");
    }

    @TaskAction
    public final void run() {
        // 1. Figure out what version of conjure-java we resolved
        VersionNumber conjureJavaVersion =
                findResolvedVersionOf(getProject(), ConjurePlugin.CONJURE_JAVA, ConjurePlugin.CONJURE_JAVA_BINARY);

        // 2. Ensure in each subproject, the version of conjure-lib in `compile` is the same.
        ConjurePlugin.JAVA_PROJECT_SUFFIXES.stream()
                .map(suffix -> getProject().findProject(getProject().getName() + suffix))
                .filter(Objects::nonNull)
                .forEach(subproj -> {
                    VersionNumber conjureJavaLibVersion = findResolvedVersionOf(
                            subproj,
                            JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME,
                            ConjurePlugin.CONJURE_JAVA_LIB_DEP);
                    boolean compatible = conjureJavaLibVersion.compareTo(conjureJavaVersion) >= 0;
                    Preconditions.checkState(
                            compatible,
                            "conjure-lib should be at least as new as the generator:\n" + "%s -> %s\n%s -> %s",
                            ConjurePlugin.CONJURE_JAVA_BINARY,
                            conjureJavaVersion,
                            ConjurePlugin.CONJURE_JAVA_LIB_DEP,
                            conjureJavaLibVersion);
                });
    }

    private static VersionNumber findResolvedVersionOf(Project project, String configuration, String moduleId) {
        ResolutionResult conjureJavaResolutionResult = project.getConfigurations()
                .getByName(configuration)
                .getIncoming()
                .getResolutionResult();
        Optional<ResolvedComponentResult> component = conjureJavaResolutionResult.getAllComponents().stream()
                .filter(c ->
                        c.getModuleVersion() != null && moduleId.equals(c.getModuleVersion().getModule().toString()))
                .collect(MoreCollectors.toOptional());
        String version = component
                .orElseThrow(
                        () -> new RuntimeException(String.format("Expected to find %s in %s", moduleId, configuration)))
                .getModuleVersion()
                .getVersion();
        return VersionNumber.parse(version);
    }
}
