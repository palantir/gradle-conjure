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

import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.conjure.api.ConjureExtension;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.io.File;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.util.GUtil;

public final class ConjureLocalPlugin implements Plugin<Project> {
    private static final String CONJURE_CONFIGURATION = "conjure";

    private static final String PYTHON_PROJECT_NAME = "python";
    private static final String TYPESCRIPT_PROJECT_NAME = "typescript";
    private static final ImmutableSet<String> FIRST_CLASS_GENERATOR_PROJECT_NAMES = ImmutableSet.of(
            PYTHON_PROJECT_NAME, TYPESCRIPT_PROJECT_NAME);

    @Override
    public void apply(Project project) {
        Configuration conjureIrConfiguration = project.getConfigurations().maybeCreate(CONJURE_CONFIGURATION);
        ConjureExtension extension = project.getExtensions()
                .create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        Configuration conjureGeneratorsConfiguration = project.getConfigurations().maybeCreate("conjureGenerators");

        Task generateConjure = project.getTasks().create("generateConjure", task -> {
            task.setDescription("Generates code for all requested languages (for which there is a subproject) "
                    + "from remote Conjure definitions.");
            task.setGroup(ConjurePlugin.TASK_GROUP);
        });
        setupConjurePython(project, extension::getPython, conjureIrConfiguration, generateConjure);
        setupConjureTypeScript(project, extension::getTypescript, conjureIrConfiguration, generateConjure);
        setupGenericConjureProjects(
                project, extension, conjureIrConfiguration, generateConjure, conjureGeneratorsConfiguration);
    }

    private void setupGenericConjureProjects(
            Project project,
            ConjureExtension conjureExtension,
            Configuration conjureIrConfiguration,
            Task generateConjure,
            Configuration conjureGeneratorsConfiguration) {
        Map<String, Dependency> generators = conjureGeneratorsConfiguration
                .getAllDependencies()
                .stream()
                .collect(Collectors.toMap(Dependency::getName, Function.identity()));

        project.getChildProjects().entrySet().stream()
                .filter(e -> !FIRST_CLASS_GENERATOR_PROJECT_NAMES.contains(e.getKey()))
                .forEach(e -> {
                    String subProjectName = e.getKey();
                    Project subproject = e.getValue();
                    if (!generators.containsKey(subProjectName)) {
                        throw new RuntimeException(String.format("Discovered subproject %s without corresponding "
                                        + "generator dependency with name '%s'",
                                subproject.getPath(), subProjectName));
                    }
                    Configuration generatorConfig = subproject.getConfigurations().maybeCreate("generator");
                    subproject.getDependencies().add(generatorConfig.getName(), generators.get(subProjectName));
                    File generatorDir = new File(subproject.getBuildDir(), "generator");

                    ExtractExecutableTask extractConjureGeneratorTask = ExtractExecutableTask.createExtractTask(
                            project,
                            GUtil.toLowerCamelCase("extractConjure " + subProjectName),
                            generatorConfig,
                            generatorDir,
                            String.format("conjure-%s", subProjectName));

                    ConjureLocalGenerateTask conjureLocalGenerateTask = project
                            .getTasks()
                            .create(GUtil.toLowerCamelCase("generate " + subProjectName),
                                    ConjureLocalGenerateTask.class,
                                    task -> {
                                        task.setDescription(String.format(
                                                "Generates %s files from remote Conjure definitions.", subProjectName));
                                        task.setGroup(ConjurePlugin.TASK_GROUP);
                                        task.setSource(conjureIrConfiguration);
                                        task.setExecutablePath(extractConjureGeneratorTask::getExecutable);
                                        task.setOptions(() -> conjureExtension.getGenericOptions(subProjectName));
                                        task.setOutputDirectory(subproject.file(subProjectName));
                                        task.dependsOn(extractConjureGeneratorTask);
                                    });
                    generateConjure.dependsOn(conjureLocalGenerateTask);
                });
    }

    private void setupConjurePython(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        if (project.findProject(PYTHON_PROJECT_NAME) == null) {
            return;
        }
        Configuration conjurePythonConfig = project.getConfigurations().maybeCreate(ConjurePlugin.CONJURE_PYTHON);

        project.project(PYTHON_PROJECT_NAME, (subproj) -> {
            File conjurePythonDir = new File(project.getBuildDir(), ConjurePlugin.CONJURE_PYTHON);
            project.getDependencies().add(ConjurePlugin.CONJURE_PYTHON, ConjurePlugin.CONJURE_PYTHON_BINARY);

            ExtractExecutableTask extractConjurePythonTask = ExtractExecutableTask.createExtractTask(
                    project,
                    "extractConjurePython",
                    conjurePythonConfig,
                    conjurePythonDir,
                    "conjure-python");

            project.getTasks().create("generatePython", ConjureLocalGenerateTask.class, task -> {
                task.setDescription("Generates Python files from remote Conjure definitions.");
                task.setGroup(ConjurePlugin.TASK_GROUP);
                task.setSource(conjureIrConfiguration);
                task.setExecutablePath(extractConjurePythonTask::getExecutable);
                task.setOutputDirectory(subproj.file("python"));
                task.setOptions(() -> {
                    GeneratorOptions generatorOptions = new GeneratorOptions(optionsSupplier.get());
                    // TODO(forozco): remove once rawSource option is added
                    generatorOptions.setProperty("packageName", "foo");
                    generatorOptions.setProperty("packageVersion", "0.0.0");
                    return generatorOptions;
                });
                task.dependsOn(extractConjurePythonTask);
                generateConjure.dependsOn(task);
            });
        });

    }

    private void setupConjureTypeScript(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        if (project.findProject(TYPESCRIPT_PROJECT_NAME) == null) {
            return;
        }
        Configuration conjureTypeScriptConfig = project.getConfigurations()
                .maybeCreate(ConjurePlugin.CONJURE_TYPESCRIPT);
        project.project(TYPESCRIPT_PROJECT_NAME, (subproj) -> {
            File conjureTypescriptDir = new File(project.getBuildDir(), ConjurePlugin.CONJURE_TYPESCRIPT);
            File srcDirectory = subproj.file("src");
            project.getDependencies().add(
                    ConjurePlugin.CONJURE_TYPESCRIPT, ConjurePlugin.CONJURE_TYPESCRIPT_BINARY);

            ExtractExecutableTask extractConjureTypeScriptTask = ExtractExecutableTask.createExtractTask(
                    project,
                    "extractConjureTypeScript",
                    conjureTypeScriptConfig,
                    conjureTypescriptDir,
                    "conjure-typescript");

            project.getTasks().create("generateTypeScript", ConjureLocalGenerateTask.class, task -> {
                task.setDescription("Generate Typescript bindings from remote Conjure definitions.");
                task.setGroup(ConjurePlugin.TASK_GROUP);
                task.setSource(conjureIrConfiguration);
                task.setExecutablePath(extractConjureTypeScriptTask::getExecutable);
                task.setOptions(() -> optionsSupplier.get().addFlag("rawSource"));
                task.setOutputDirectory(srcDirectory);
                task.dependsOn(extractConjureTypeScriptTask);
                generateConjure.dependsOn(task);
            });
        });
    }
}
