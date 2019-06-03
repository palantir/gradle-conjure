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

import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.conjure.api.ConjureExtension;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.util.GUtil;

public final class ConjureLocalJavaPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Configuration conjureIrConfiguration = project.getConfigurations().maybeCreate(
                ConjureLocalPlugin.CONJURE_CONFIGURATION);
        Configuration conjureGeneratorsConfiguration = project.getConfigurations().maybeCreate(
                ConjureLocalPlugin.CONJURE_GENERATORS_CONFIGURATION_NAME);

        ConjureExtension extension = project.getExtensions()
                .create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        Task generateConjure = project.getTasks().create("generateConjure", task -> {
            task.setDescription("Generates Java code for all requested generators (for which there is a subproject) "
                    + "from remote Conjure definitions.");
            task.setGroup(ConjurePlugin.TASK_GROUP);
        });
        setupGenericJavaProjects(
                project, extension, conjureIrConfiguration, generateConjure, conjureGeneratorsConfiguration);
    }

    private void setupGenericJavaProjects(
            Project project,
            ConjureExtension conjureExtension,
            Configuration conjureIrConfiguration,
            Task generateConjure,
            Configuration conjureGeneratorsConfiguration) {
        ConjureGenericCodeGen.validateProjectNamesMapToGeneratorNames(
                project, conjureGeneratorsConfiguration, ImmutableSet.of());

        project.getChildProjects().values().stream()
                .forEach(subProject -> {

                    ExtractExecutableTask extractConjureGeneratorTask =
                            ConjureGenericCodeGen.createExtractExecutableTask(
                                    subProject, conjureGeneratorsConfiguration);

                    ConjureLocalJavaGenerateTask conjureLocalGenerateTask = project
                            .getTasks()
                            .create(GUtil.toLowerCamelCase("generateConjureJava " + subProject.getName()),
                                    ConjureLocalJavaGenerateTask.class,
                                    task -> {
                                        task.setDescription(String.format(
                                                "Generates %s Java files from remote Conjure definitions.",
                                                subProject.getName()));
                                        task.setGroup(ConjurePlugin.TASK_GROUP);
                                        task.setSource(conjureIrConfiguration);
                                        task.setExecutablePath(extractConjureGeneratorTask::getExecutable);
                                        task.setOptions(() -> conjureExtension.getGenericOptions(subProject.getName()));
                                        task.setOutputDirectory(
                                                subProject.file(ConjurePlugin.JAVA_GENERATED_SOURCE_DIRNAME));
                                        task.dependsOn(extractConjureGeneratorTask);
                                    });
                    generateConjure.dependsOn(conjureLocalGenerateTask);
                    ConjurePlugin.applyDependencyForIdeTasks(subProject, conjureLocalGenerateTask);

                    conjureLocalGenerateTask.doLast(new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            subProject.getPluginManager().apply(JavaPlugin.class);
                            ConjurePlugin.addGeneratedToMainSourceSet(subProject);
                        }
                    });
                    conjureLocalGenerateTask.dependsOn(
                            ConjurePlugin.createWriteGitignoreTask(
                                    subProject,
                                    GUtil.toLowerCamelCase("gitignoreConjureJava " + subProject.getName()),
                                    subProject.getProjectDir(),
                                    ConjurePlugin.JAVA_GITIGNORE_CONTENTS));
                });
    }
}
