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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.gradle.conjure.api.ConjureExtension;
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.RecommendedProductDependenciesExtension;
import com.palantir.gradle.dist.RecommendedProductDependenciesPlugin;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

public final class ConjureJavaLocalCodegenPlugin implements Plugin<Project> {
    private static final ObjectMapper OBJECT_MAPPER = ObjectMappers.newClientObjectMapper();
    private static final String CONJURE_CONFIGURATION = "conjure";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaBasePlugin.class);

        ConjureExtension extension =
                project.getExtensions().create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        Configuration conjureIrConfiguration = project.getConfigurations().create(CONJURE_CONFIGURATION);
        TaskProvider<ExtractConjureIrTask> extractConjureIr = project.getTasks()
                .register("extractConjureIr", ExtractConjureIrTask.class, task -> {
                    task.getIrConfiguration().set(conjureIrConfiguration);
                });

        TaskProvider<ExtractExecutableTask> extractJavaTask = ExtractConjurePlugin.applyConjureJava(project);

        setupSubprojects(project, extension, extractJavaTask, extractConjureIr, conjureIrConfiguration);
    }

    private static void setupSubprojects(
            Project project,
            ConjureExtension extension,
            TaskProvider<ExtractExecutableTask> extractJavaTask,
            TaskProvider<ExtractConjureIrTask> extractConjureIr,
            Configuration conjureIrConfiguration) {

        // Validating that each subproject has a corresponding definition and vice versa.
        // We do this in afterEvaluate to ensure the configuration is populated.
        project.afterEvaluate(_p -> {
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

        project.getChildProjects().forEach((_name, subproject) -> {
            subproject.getPluginManager().apply(JavaLibraryPlugin.class);
            subproject.getPluginManager().apply(RecommendedProductDependenciesPlugin.class);
            createGenerateTask(subproject, extension, extractJavaTask, extractConjureIr);
        });
    }

    private static void createGenerateTask(
            Project project,
            ConjureExtension extension,
            TaskProvider<ExtractExecutableTask> extractJavaTask,
            TaskProvider<ExtractConjureIrTask> extractConjureIr) {
        ConjurePlugin.ignoreFromCheckUnusedDependencies(project);
        ConjurePlugin.addGeneratedToMainSourceSet(project);

        project.getDependencies().add("api", Dependencies.CONJURE_JAVA_LIB);
        project.getDependencies().add("implementation", Dependencies.JETBRAINS_ANNOTATIONS);
        project.getDependencies().add("compileOnly", Dependencies.ANNOTATION_API);

        TaskProvider<WriteGitignoreTask> generateGitIgnore = ConjurePlugin.createWriteGitignoreTask(
                project, "gitignoreConjure", project.getProjectDir(), ConjurePlugin.JAVA_GITIGNORE_CONTENTS);

        Provider<File> conjureIrFile = extractConjureIr
                .flatMap(task -> task.getConjureIr().file(project.getName() + ".conjure.json"))
                .map(RegularFile::getAsFile);

        project.getExtensions()
                .getByType(RecommendedProductDependenciesExtension.class)
                .getRecommendedProductDependenciesProvider()
                .set(conjureIrFile.map(ConjureJavaLocalCodegenPlugin::extractProductDependencies));

        TaskProvider<ConjureJavaLocalGeneratorTask> generateJava = project.getTasks()
                .register("generateConjure", ConjureJavaLocalGeneratorTask.class, task -> {
                    task.setSource(conjureIrFile);
                    task.getExecutablePath()
                            .set(project.getLayout()
                                    .file(extractJavaTask.map(t -> OsUtils.appendDotBatIfWindows(
                                            t.getExecutable().getAsFile().get()))));
                    task.getOptions().set(project.provider(() -> {
                        Map<String, Object> properties =
                                new HashMap<>(extension.getJava().getProperties());
                        properties.putIfAbsent(
                                "packagePrefix",
                                sanitizePackageName(project.getGroup().toString()));
                        return properties;
                    }));
                    task.getOutputDirectory().set(project.file(ConjurePlugin.JAVA_GENERATED_SOURCE_DIRNAME));
                    task.dependsOn(extractJavaTask, extractConjureIr, generateGitIgnore);
                });

        project.getTasks().named("compileJava").configure(compileJava -> compileJava.dependsOn(generateJava));
        ConjurePlugin.applyDependencyForIdeTasks(project, generateJava);
    }

    /**
     * Maven groups can have dashes, java packages can't.
     * https://docs.oracle.com/javase/tutorial/java/package/namingpkgs.html.
     */
    private static String sanitizePackageName(String group) {
        return group.replaceAll("-", "");
    }

    private static Set<ProductDependency> extractProductDependencies(File irFile) {
        try {
            MinimalConjureDefinition conjureDefinition =
                    OBJECT_MAPPER.readValue(irFile, MinimalConjureDefinition.class);
            return conjureDefinition
                    .extensions()
                    .map(MinimalConjureDefinition.Extensions::productDependencies)
                    .orElseGet(Collections::emptySet);
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to parse conjure definition", e);
        }
    }
}
