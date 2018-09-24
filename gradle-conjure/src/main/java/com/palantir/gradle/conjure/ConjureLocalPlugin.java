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
        setupConjurePython(project, extension::getPython, conjureIrConfiguration, generateConjure);
        setupConjureTypeScript(project, extension::getTypescript, conjureIrConfiguration, generateConjure);
    }

    private void setupConjurePython(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        if (project.findProject("python") == null) {
            return;
        }
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

    private void setupConjureTypeScript(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            Task generateConjure) {
        if (project.findProject("typescript") == null) {
            return;
        }
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
                task.setOptions(() -> optionsSupplier.get().addFlag("rawSource"));
                task.setOutputDirectory(srcDirectory);
                task.dependsOn(extractConjureTypeScriptTask);
                generateConjure.dependsOn(task);
            });
        });
    }
}
