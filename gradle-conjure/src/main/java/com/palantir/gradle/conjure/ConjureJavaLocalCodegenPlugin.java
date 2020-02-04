/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.Sets;
import com.palantir.gradle.conjure.api.ConjureExtension;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.io.File;
import java.util.Set;
import java.util.regex.Pattern;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

public final class ConjureJavaLocalCodegenPlugin implements Plugin<Project> {
    private static final String CONJURE_CONFIGURATION = "conjure";
    private static final Pattern DEFINITION_NAME =
            Pattern.compile("(.*)-([0-9]+\\.[0-9]+\\.[0-9]+(?:-rc[0-9]+)?(?:-[0-9]+-g[a-f0-9]+)?)(.conjure)?.json");

    static final String CONJURE_JAVA = "conjureJava";
    static final String CONJURE_JAVA_BINARY = "com.palantir.conjure.java:conjure-java";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);

        ConjureExtension extension =
                project.getExtensions().create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        Configuration conjureIrConfiguration = project.getConfigurations().maybeCreate(CONJURE_CONFIGURATION);
        TaskProvider<Copy> extractConjureIr = project.getTasks().register("extractConjureIr", Copy.class, task -> {
            task.rename(DEFINITION_NAME, "$1.conjure.json");
            task.from(conjureIrConfiguration);
            task.into(project.getLayout().getBuildDirectory().dir("conjure-ir"));
        });

        Configuration conjureJavaConfig = project.getConfigurations().maybeCreate(CONJURE_JAVA);
        File conjureJavaDir = new File(project.getBuildDir(), CONJURE_JAVA);
        project.getDependencies().add(CONJURE_JAVA, CONJURE_JAVA_BINARY);
        ExtractExecutableTask extractJavaTask = ExtractExecutableTask.createExtractTask(
                project, "extractConjureJava", conjureJavaConfig, conjureJavaDir, "conjure-java");

        setupSubprojects(project, extension, extractJavaTask, extractConjureIr, conjureIrConfiguration);
    }

    private static void setupSubprojects(
            Project project,
            ConjureExtension extension,
            ExtractExecutableTask extractJavaTask,
            TaskProvider<Copy> extractConjureIr,
            Configuration conjureIrConfiguration) {

        // Validating that each subproject has a corresponding definition and vice versa.
        // We do this in afterEvaluate to ensure the configuration is populated.
        project.afterEvaluate(p -> {
            Set<String> apis = conjureIrConfiguration.getAllDependencies().stream()
                    .map(Dependency::getName)
                    .collect(ImmutableSet.toImmutableSet());

            Sets.SetView<String> missingProjects =
                    Sets.difference(apis, project.getChildProjects().keySet());
            if (!missingProjects.isEmpty()) {
                throw new RuntimeException(String.format(
                        "Discovered dependencies %s without corresponding subprojects.", missingProjects));
            }
            Sets.SetView<String> missingApis =
                    Sets.difference(project.getChildProjects().keySet(), apis);
            if (!missingApis.isEmpty()) {
                throw new RuntimeException(
                        String.format("Discovered subprojects %s without corresponding dependencies.", missingApis));
            }
        });

        project.getChildProjects().forEach((name, subproject) -> {
            subproject.getPluginManager().apply(JavaLibraryPlugin.class);
            createGenerateTask(subproject, extension, extractJavaTask, extractConjureIr);
        });
    }

    private static void createGenerateTask(
            Project project,
            ConjureExtension extension,
            ExtractExecutableTask extractJavaTask,
            TaskProvider<Copy> extractConjureIr) {
        Task generateGitIgnore = ConjurePlugin.createWriteGitignoreTask(
                project, "gitignoreConjure", project.getProjectDir(), ConjurePlugin.JAVA_GITIGNORE_CONTENTS);
        TaskProvider<ConjureJavaLocalGeneratorTask> generateJava = project.getTasks()
                .register("generateConjure", ConjureJavaLocalGeneratorTask.class, task -> {
                    task.setSource(extractConjureIr.map(
                            irTask -> new File(irTask.getDestinationDir(), project.getName() + ".conjure.json")));
                    task.getExecutablePath().set(project.getLayout().file(project.provider(() ->
                            OsUtils.appendDotBatIfWindows(extractJavaTask.getExecutable()))));
                    task.getOptions().set(project.provider(() -> {
                        GeneratorOptions options = new GeneratorOptions(extension.getJava());
                        options.setProperty("packagePrefix", project.getGroup().toString());
                        return options.getProperties();
                    }));
                    task.getOutputDirectory().set(project.file(ConjurePlugin.JAVA_GENERATED_SOURCE_DIRNAME));
                    task.dependsOn(extractJavaTask, extractConjureIr, generateGitIgnore);
                });
        project.getTasks().named("compileJava").configure(compileJava -> compileJava.dependsOn(generateJava));
        ConjurePlugin.applyDependencyForIdeTasks(project, generateJava.get());
    }
}
