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

import com.palantir.gradle.conjure.api.ConjureExtension;
import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

public final class ConjureBasePlugin implements Plugin<Project> {
    static final Attribute<Usage> CONJURE_USAGE = Attribute.of("com.palantir.conjure", Usage.class);

    static final String COMPILE_IR_TASK = "compileIr";
    static final String SERVICE_DEPENDENCIES_TASK = "generateConjureServiceDependencies";

    static final String CONJURE_IR_CONFIGURATION = "conjureIr";

    static final String TASK_GROUP = "Conjure";

    private TaskProvider<CompileIrTask> compileIrProvider = null;
    private ConjureExtension conjureExtension = null;

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);
        ConjureProductDependenciesExtension conjureProductDependenciesExtension = project.getExtensions()
                .create(
                        ConjureProductDependenciesExtension.EXTENSION_NAME,
                        ConjureProductDependenciesExtension.class,
                        project);
        conjureExtension = project.getExtensions().create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);
        SourceDirectorySet conjureSourceSet = createConjureSourceSet(project);
        TaskProvider<Copy> copyConjureSourcesTask = createCopyConjureSourceTask(project, conjureSourceSet);
        compileIrProvider =
                createIrTasks(project, conjureProductDependenciesExtension, conjureExtension, copyConjureSourcesTask);
        createServiceDependenciesTask(project, conjureProductDependenciesExtension);
        createOutgoingConfiguration(project, compileIrProvider);
    }

    ConjureExtension conjureExtension() {
        return Objects.requireNonNull(
                conjureExtension, "ConjureExtension task has not been registered. Has this plugin been applied?");
    }

    TaskProvider<CompileIrTask> compileIrProvider() {
        return Objects.requireNonNull(
                compileIrProvider, "compileIr task has not been registered. Has this plugin been applied?");
    }

    private static SourceDirectorySet createConjureSourceSet(Project project) {
        // Conjure code source set
        SourceDirectorySet conjureSourceSet = project.getObjects().sourceDirectorySet("conjure", "conjure");
        conjureSourceSet.setSrcDirs(Collections.singleton("src/main/conjure"));
        conjureSourceSet.setIncludes(Collections.singleton("**/*.yml"));

        return conjureSourceSet;
    }

    private static TaskProvider<CompileIrTask> createIrTasks(
            Project project,
            ConjureProductDependenciesExtension pdepsExtension,
            ConjureExtension conjureExtension,
            TaskProvider<Copy> copyConjureSourcesTask) {
        TaskProvider<ExtractExecutableTask> extractCompilerTask = ExtractConjurePlugin.applyConjureCompiler(project);

        Provider<Directory> irDir = project.getLayout().getBuildDirectory().dir("conjure-ir");

        project.getTasks().register("rawIr", CompileIrTask.class, rawIr -> {
            rawIr.getInputDirectory().set(project.getLayout().dir(copyConjureSourcesTask.map(Copy::getDestinationDir)));
            rawIr.getExecutableDir().set(extractCompilerTask.flatMap(ExtractExecutableTask::getOutputDirectory));
            rawIr.getOutputIrFile().set(irDir.map(dir -> dir.file("rawIr.conjure.json")));
            rawIr.getOptions()
                    .set(project.provider(() -> conjureExtension.getParser().getProperties()));
            rawIr.dependsOn(copyConjureSourcesTask);
            rawIr.dependsOn(extractCompilerTask);
        });

        return project.getTasks().register(COMPILE_IR_TASK, CompileIrTask.class, compileIr -> {
            compileIr.setDescription("Converts your Conjure YML files into a single portable JSON file in IR format.");
            compileIr.setGroup(ConjureBasePlugin.TASK_GROUP);

            compileIr
                    .getInputDirectory()
                    .set(project.getLayout().dir(copyConjureSourcesTask.map(Copy::getDestinationDir)));
            compileIr.getExecutableDir().set(extractCompilerTask.flatMap(ExtractExecutableTask::getOutputDirectory));
            compileIr.getOutputIrFile().set(irDir.map(dir -> dir.file(project.getName() + ".conjure.json")));
            compileIr.getProductDependencies().set(project.provider(pdepsExtension::getProductDependencies));
            compileIr
                    .getOptions()
                    .set(project.provider(() -> conjureExtension.getParser().getProperties()));
            compileIr.dependsOn(copyConjureSourcesTask);
            compileIr.dependsOn(extractCompilerTask);
        });
    }

    private static void createOutgoingConfiguration(Project project, TaskProvider<CompileIrTask> compileIr) {
        Configuration conjureIr = project.getConfigurations().create(CONJURE_IR_CONFIGURATION, conf -> {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(true);
            conf.setVisible(true);
            conf.getAttributes().attribute(CONJURE_USAGE, project.getObjects().named(Usage.class, "conjure"));
        });
        project.getArtifacts()
                .add(
                        conjureIr.getName(),
                        compileIr.flatMap(CompileIrTask::getOutputIrFile),
                        artifact -> artifact.builtBy(compileIr));
    }

    private static TaskProvider<Copy> createCopyConjureSourceTask(Project project, SourceDirectorySet sourceset) {
        File buildDir = new File(project.getBuildDir(), "conjure");
        TaskProvider<Copy> copyConjureSourcesTask = project.getTasks()
                .register("copyConjureSourcesIntoBuild", Copy.class, task -> {
                    task.into(buildDir).from(sourceset);

                    // Replacing this with a lambda is not supported for build caching
                    // (see https://github.com/gradle/gradle/issues/5510)
                    task.doFirst(new Action<Task>() {
                        @Override
                        public void execute(Task _task) {
                            try {
                                FileUtils.deleteDirectory(buildDir);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                });

        ConjurePlugin.registerClean(project, copyConjureSourcesTask);

        return copyConjureSourcesTask;
    }

    private static void createServiceDependenciesTask(Project project, ConjureProductDependenciesExtension ext) {
        project.getTasks().register(SERVICE_DEPENDENCIES_TASK, GenerateConjureServiceDependenciesTask.class, task -> {
            task.setConjureServiceDependencies(ext::getProductDependencies);
        });
    }
}
