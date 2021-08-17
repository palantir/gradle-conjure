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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
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
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GUtil;

public final class ConjureLocalPlugin implements Plugin<Project> {
    private static final String CONJURE_CONFIGURATION = "conjure";

    private static final String JAVA_PROJECT_NAME = "java";
    private static final String PYTHON_PROJECT_NAME = "python";
    private static final String TYPESCRIPT_PROJECT_NAME = "typescript";
    private static final ImmutableSet<String> FIRST_CLASS_GENERATOR_PROJECT_NAMES =
            ImmutableSet.of(JAVA_PROJECT_NAME, PYTHON_PROJECT_NAME, TYPESCRIPT_PROJECT_NAME);
    private static final ImmutableSet<String> UNSAFE_JAVA_OPTIONS =
            ImmutableSet.of("objects", "retrofit", "jersey", "undertow");

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);

        Configuration conjureIrConfiguration = project.getConfigurations().maybeCreate(CONJURE_CONFIGURATION);
        Configuration conjureGeneratorsConfiguration =
                project.getConfigurations().maybeCreate(ConjurePlugin.CONJURE_GENERATORS_CONFIGURATION_NAME);

        ConjureExtension extension =
                project.getExtensions().create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        TaskProvider<Task> generateConjure = project.getTasks().register("generateConjure", task -> {
            task.setDescription("Generates code for all requested languages (for which there is a subproject) "
                    + "from remote Conjure definitions.");
            task.setGroup(ConjurePlugin.TASK_GROUP);
        });
        setupConjureJava(
                project, immutableOptionsSupplier(extension::getJava), conjureIrConfiguration, generateConjure);
        setupConjurePython(
                project, immutableOptionsSupplier(extension::getPython), conjureIrConfiguration, generateConjure);
        setupConjureTypeScript(
                project, immutableOptionsSupplier(extension::getTypescript), conjureIrConfiguration, generateConjure);
        setupGenericConjureProjects(
                project, extension, conjureIrConfiguration, generateConjure, conjureGeneratorsConfiguration);
    }

    private void setupConjureJava(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            TaskProvider<Task> generateConjure) {
        Project subproj = project.findProject(JAVA_PROJECT_NAME);
        if (subproj == null) {
            return;
        }

        ExtractExecutableTask extractJavaTask = ExtractConjurePlugin.applyConjureJava(project);

        subproj.getPluginManager().apply(JavaLibraryPlugin.class);
        ConjurePlugin.addGeneratedToMainSourceSet(subproj);

        TaskProvider<WriteGitignoreTask> gitignoreConjureJava = ConjurePlugin.createWriteGitignoreTask(
                subproj, "gitignoreConjureJava", subproj.getProjectDir(), ConjurePlugin.JAVA_GITIGNORE_CONTENTS);

        TaskProvider<ConjureLocalGenerateGenericTask> generateJava = project.getTasks()
                .register("generateJava", ConjureLocalGenerateGenericTask.class, task -> {
                    task.setDescription("Generates Java bindings for remote Conjure definitions.");
                    task.setGroup(ConjurePlugin.TASK_GROUP);
                    // TODO(forozco): Automatically pass which category of code to generate
                    task.setOptions(() -> {
                        GeneratorOptions generatorOptions = optionsSupplier.get();
                        Preconditions.checkArgument(
                                UNSAFE_JAVA_OPTIONS.stream().noneMatch(generatorOptions::has),
                                "Unable to generate Java bindings since unsafe options were provided",
                                generatorOptions.getProperties());

                        return generatorOptions;
                    });
                    task.setSource(conjureIrConfiguration);
                    task.setExecutablePath(extractJavaTask::getExecutable);
                    task.setOutputDirectory(subproj.file(ConjurePlugin.JAVA_GENERATED_SOURCE_DIRNAME));

                    task.dependsOn(gitignoreConjureJava);
                    task.dependsOn(extractJavaTask);

                    subproj.getDependencies().add("api", subproj);
                });
        generateConjure.configure(t -> t.dependsOn(generateJava));

        subproj.getTasks().named("compileJava").configure(t -> t.dependsOn(generateJava));
        ConjurePlugin.registerClean(project, "cleanGenerateJava");
        ConjurePlugin.applyDependencyForIdeTasks(subproj, generateJava);
    }

    private void setupGenericConjureProjects(
            Project project,
            ConjureExtension conjureExtension,
            Configuration conjureIrConfiguration,
            TaskProvider<Task> generateConjure,
            Configuration conjureGeneratorsConfiguration) {
        // Validating that each subproject has a corresponding generator.
        // We do this in afterEvaluate to ensure the configuration is populated.
        Map<String, Project> genericSubProjects =
                Maps.filterKeys(project.getChildProjects(), key -> !FIRST_CLASS_GENERATOR_PROJECT_NAMES.contains(key));
        if (genericSubProjects.isEmpty()) {
            return;
        }

        project.afterEvaluate(_p -> {
            Map<String, Dependency> generators = conjureGeneratorsConfiguration.getAllDependencies().stream()
                    .collect(Collectors.toMap(
                            dependency -> {
                                Preconditions.checkState(
                                        dependency.getName().startsWith(ConjurePlugin.CONJURE_GENERATOR_DEP_PREFIX),
                                        "Generators should start with '%s' according to conjure RFC 002, "
                                                + "but found name: '%s' (%s)",
                                        ConjurePlugin.CONJURE_GENERATOR_DEP_PREFIX,
                                        dependency.getName(),
                                        dependency);
                                return dependency
                                        .getName()
                                        .substring(ConjurePlugin.CONJURE_GENERATOR_DEP_PREFIX.length());
                            },
                            Function.identity()));

            genericSubProjects.forEach((subprojectName, subproject) -> {
                if (!generators.containsKey(subprojectName)) {
                    throw new RuntimeException(String.format(
                            "Discovered subproject %s without corresponding " + "generator dependency with name '%s'",
                            subproject.getPath(), ConjurePlugin.CONJURE_GENERATOR_DEP_PREFIX + subprojectName));
                }
            });
        });

        genericSubProjects.forEach((subprojectName, subproject) -> {
            // We create a lazy filtered FileCollection to avoid using afterEvaluate.
            FileCollection matchingGeneratorDeps = conjureGeneratorsConfiguration.fileCollection(
                    dep -> dep.getName().equals(ConjurePlugin.CONJURE_GENERATOR_DEP_PREFIX + subprojectName));

            ExtractExecutableTask extractConjureGeneratorTask = ExtractExecutableTask.createExtractTask(
                    project,
                    GUtil.toLowerCamelCase("extractConjure " + subprojectName),
                    matchingGeneratorDeps,
                    new File(subproject.getBuildDir(), "generator"),
                    String.format("conjure-%s", subprojectName));

            TaskProvider<ConjureLocalGenerateGenericTask> conjureLocalGenerateTask = project.getTasks()
                    .register(
                            GUtil.toLowerCamelCase("generate " + subprojectName),
                            ConjureLocalGenerateGenericTask.class,
                            task -> {
                                task.setDescription(String.format(
                                        "Generates %s files from remote Conjure definitions.", subprojectName));
                                task.setGroup(ConjurePlugin.TASK_GROUP);
                                task.setSource(conjureIrConfiguration);
                                task.setExecutablePath(extractConjureGeneratorTask::getExecutable);
                                task.setOptions(() -> conjureExtension.getGenericOptions(subprojectName));
                                task.setOutputDirectory(subproject.file(subprojectName));
                                task.dependsOn(extractConjureGeneratorTask);
                            });
            generateConjure.configure(t -> t.dependsOn(conjureLocalGenerateTask));
        });
    }

    private void setupConjurePython(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            TaskProvider<Task> generateConjure) {
        Project subproj = project.findProject(PYTHON_PROJECT_NAME);
        if (subproj == null) {
            return;
        }

        ExtractExecutableTask extractConjurePythonTask = ExtractConjurePlugin.applyConjurePython(project);

        TaskProvider<ConjureLocalGenerateTask> conjureLocalGenerateTask = project.getTasks()
                .register("generatePython", ConjureLocalGenerateTask.class, task -> {
                    task.setDescription("Generates Python files from remote Conjure definitions.");
                    task.setGroup(ConjurePlugin.TASK_GROUP);
                    task.setSource(conjureIrConfiguration);
                    task.setExecutablePath(extractConjurePythonTask::getExecutable);
                    task.setOutputDirectory(subproj.file("python"));
                    task.setOptions(() -> optionsSupplier.get().addFlag("rawSource"));
                    task.dependsOn(extractConjurePythonTask);
                });
        generateConjure.configure(t -> t.dependsOn(conjureLocalGenerateTask));
    }

    private void setupConjureTypeScript(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Configuration conjureIrConfiguration,
            TaskProvider<Task> generateConjure) {
        Project subproj = project.findProject(TYPESCRIPT_PROJECT_NAME);
        if (subproj == null) {
            return;
        }
        File srcDirectory = subproj.file("src");

        ExtractExecutableTask extractConjureTypeScriptTask = ExtractConjurePlugin.applyConjureTypeScript(project);

        TaskProvider<ConjureLocalGenerateTask> conjureLocalGenerateTask = project.getTasks()
                .register("generateTypeScript", ConjureLocalGenerateTask.class, task -> {
                    task.setDescription("Generate Typescript bindings from remote Conjure definitions.");
                    task.setGroup(ConjurePlugin.TASK_GROUP);
                    task.setSource(conjureIrConfiguration);
                    task.setExecutablePath(extractConjureTypeScriptTask::getExecutable);
                    task.setOptions(() -> optionsSupplier.get().addFlag("rawSource"));
                    task.setOutputDirectory(srcDirectory);
                    task.dependsOn(extractConjureTypeScriptTask);
                });
        generateConjure.configure(t -> t.dependsOn(conjureLocalGenerateTask));
    }

    private static Supplier<GeneratorOptions> immutableOptionsSupplier(Supplier<GeneratorOptions> supplier) {
        return () -> new GeneratorOptions(supplier.get());
    }
}
