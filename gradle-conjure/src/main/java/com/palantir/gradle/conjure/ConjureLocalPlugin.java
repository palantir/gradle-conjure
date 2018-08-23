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

import com.palantir.gradle.conjure.api.ConjureExtension;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import com.sun.xml.internal.ws.util.StringUtils;
import java.io.File;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;

public final class ConjureLocalPlugin implements Plugin<Project> {
    private static final String CONJURE_CONFIGURATION = "conjure";

    @Override
    public void apply(Project project) {
        Configuration conjureIrConfiguration = project.getConfigurations().maybeCreate(CONJURE_CONFIGURATION);
        ConjureExtension extension = project.getExtensions()
                .create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        Task generateConjure = project.getTasks().create("generateConjure", DefaultTask.class);

        setupConjureJava(project, extension::getJava, conjureIrConfiguration, generateConjure);
        setupConjurePython(project, extension::getPython, conjureIrConfiguration, generateConjure);
        setupConjureTypeScriptProject(project, extension::getTypescript, conjureIrConfiguration, generateConjure);
        setupGenericConjureProjects(project, extension, conjureIrConfiguration, generateConjure);
    }

    private void setupGenericConjureProjects(
            Project project,
            ConjureExtension conjureExtension,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        project.getChildProjects().entrySet().stream()
                .filter(e -> e.getKey().startsWith("conjure-"))
                .forEach(e -> {
                    String subProjectName = e.getKey();
                    String generatorName = subProjectName.replaceFirst("conjure-","");
                    Configuration generatorConfig = project.getConfigurations().maybeCreate(generatorName);
                    project.project(subProjectName, (subproj) -> {
                        File generatorDir = new File(project.getBuildDir(), generatorName);
                        project.getDependencies().add(generatorName,
                                String.format("com.palantir.conjure.%s:conjure-%s", generatorName, generatorName));
                        ExtractExecutableTask extractConjureGeneratorTask = ExtractExecutableTask.createExtractTask(
                                project,
                                String.format("extractConjure%s", StringUtils.capitalize(generatorName)),
                                generatorConfig,
                                generatorDir,
                                String.format("conjure-%s", generatorName));

                        project.getTasks().create(String.format("generate%s", StringUtils.capitalize(generatorName)),
                                ConjureLocalGenerateTask.class, task -> {
                            task.setDescription(String.format("Generates %s files from remote Conjure definitions.", generatorName));
                            task.setGroup(ConjurePlugin.TASK_GROUP);
                            task.setSource(conjureIrConfiguration);
                            task.setExecutablePath(extractConjureGeneratorTask::getExecutable);
                            task.setOptions(() -> conjureExtension.getGenericOptions(generatorName));
                            task.setOutputDirectory(subproj.file(generatorName));
                            task.dependsOn(extractConjureGeneratorTask);
                            generateConjure.dependsOn(task);
                        });
                    });
                });
    }

    private void setupConjureJava(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        if (project.findProject("java") != null) {
            Configuration conjureJavaConfig = project.getConfigurations().maybeCreate(ConjurePlugin.CONJURE_JAVA);
            File conjureJavaDir = new File(project.getBuildDir(), ConjurePlugin.CONJURE_JAVA);
            project.getDependencies().add(ConjurePlugin.CONJURE_JAVA, ConjurePlugin.CONJURE_JAVA_BINARY);
            ExtractExecutableTask extractJavaTask = ExtractExecutableTask.createExtractTask(
                    project,
                    "extractConjureJava",
                    conjureJavaConfig,
                    conjureJavaDir,
                    "conjure-java");

            project.project("java", subproj -> {
                Task generateJavaTask = subproj.getTasks().create("generateJava", DefaultTask.class);
                generateConjure.dependsOn(generateJavaTask);

                project.getTasks().create(
                        "generateJavaObjects",
                        ConjureGeneratorTask.class,
                        (task) -> {
                            task.setDescription("Generates POJOs from remote Conjure definitions.");
                            task.setGroup(ConjurePlugin.TASK_GROUP);
                            task.setExecutablePath(extractJavaTask::getExecutable);
                            task.setOptions(() -> GeneratorOptions.addFlag(optionsSupplier.get(), "objects"));
                            task.setOutputDirectory(subproj.file(ConjurePlugin.JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(conjureIrConfiguration);

                            task.dependsOn(extractJavaTask);
                            generateJavaTask.dependsOn(task);
                        });
                project.getTasks().create(
                        "generateJavaJersey",
                        ConjureGeneratorTask.class,
                        (task) -> {
                            task.setDescription("Generates  Jerset interfaces from remote Conjure definitions.");
                            task.setGroup(ConjurePlugin.TASK_GROUP);
                            task.setExecutablePath(extractJavaTask::getExecutable);
                            task.setOptions(() -> GeneratorOptions.addFlag(optionsSupplier.get(), "jersey"));
                            task.setOutputDirectory(subproj.file(ConjurePlugin.JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(conjureIrConfiguration);

                            task.dependsOn(extractJavaTask);
                            generateJavaTask.dependsOn(task);
                        });
            });
        }
    }

    private void setupConjurePython(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        if (project.findProject("python") != null) {
            Configuration conjurePythonConfig = project.getConfigurations().maybeCreate(ConjurePlugin.CONJURE_PYTHON);

            project.project("python", (subproj) -> {
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
    }

    private void setupConjureTypeScriptProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        if (project.findProject("typescript") != null) {
            Configuration conjureTypeScriptConfig = project.getConfigurations()
                    .maybeCreate(ConjurePlugin.CONJURE_TYPESCRIPT);
            project.project("typescript", (subproj) -> {
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
                    task.setOptions(() -> GeneratorOptions.addFlag(optionsSupplier.get(), "rawSource"));
                    task.setOutputDirectory(srcDirectory);
                    task.dependsOn(extractConjureTypeScriptTask);
                    generateConjure.dependsOn(task);
                });
            });
        }
    }
}
