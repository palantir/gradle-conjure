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

import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension;
import java.io.File;
import java.util.Collections;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.GFileUtils;

public final class ConjureBasePlugin implements Plugin<Project> {
    static final String COMPILE_IR_TASK = "compileIr";
    static final String SERVICE_DEPENDENCIES_TASK = "generateConjureServiceDependencies";

    static final String CONJURE_COMPILER = "conjureCompiler";
    static final String CONJURE_COMPILER_BINARY = "com.palantir.conjure:conjure";

    static final String TASK_GROUP = "Conjure";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);
        ConjureProductDependenciesExtension conjureProductDependenciesExtension = project.getExtensions()
                .create(ConjureProductDependenciesExtension.EXTENSION_NAME, ConjureProductDependenciesExtension.class);

        SourceDirectorySet conjureSourceSet = createConjureSourceSet(project);
        TaskProvider<Copy> copyConjureSourcesTask = createCopyConjureSourceTask(project, conjureSourceSet);
        createIrTasks(project, conjureProductDependenciesExtension, copyConjureSourcesTask);
        createServiceDependenciesTask(project, conjureProductDependenciesExtension);
    }

    private static SourceDirectorySet createConjureSourceSet(Project project) {
        // Conjure code source set
        SourceDirectorySet conjureSourceSet = project.getObjects().sourceDirectorySet("conjure", "conjure");
        conjureSourceSet.setSrcDirs(Collections.singleton("src/main/conjure"));
        conjureSourceSet.setIncludes(Collections.singleton("**/*.yml"));

        return conjureSourceSet;
    }

    private static void createIrTasks(
            Project project,
            ConjureProductDependenciesExtension pdepsExtension,
            TaskProvider<Copy> copyConjureSourcesTask) {
        Configuration conjureCompilerConfig = project.getConfigurations().maybeCreate(CONJURE_COMPILER);
        File conjureCompilerDir = new File(project.getBuildDir(), CONJURE_COMPILER);
        project.getDependencies().add(CONJURE_COMPILER, CONJURE_COMPILER_BINARY);
        ExtractExecutableTask extractCompilerTask = ExtractExecutableTask.createExtractTask(
                project, "extractConjure", conjureCompilerConfig, conjureCompilerDir, "conjure");

        Provider<Directory> irDir = project.getLayout().getBuildDirectory().dir("conjure-ir");

        project.getTasks().register("rawIr", CompileIrTask.class, rawIr -> {
            rawIr.getInputDir().set(project.getLayout().dir(copyConjureSourcesTask.map(Copy::getDestinationDir)));
            rawIr.setExecutableDir(extractCompilerTask::getOutputDirectory);
            rawIr.getOutputIrFile().set(irDir.map(dir -> dir.file("rawIr.conjure.json")));
            rawIr.dependsOn(copyConjureSourcesTask);
            rawIr.dependsOn(extractCompilerTask);
        });

        project.getTasks().register(COMPILE_IR_TASK, CompileIrTask.class, compileIr -> {
            compileIr.setDescription("Converts your Conjure YML files into a single portable JSON file in IR format.");
            compileIr.setGroup(ConjureBasePlugin.TASK_GROUP);

            compileIr.getInputDir().set(project.getLayout().dir(copyConjureSourcesTask.map(Copy::getDestinationDir)));
            compileIr.setExecutableDir(extractCompilerTask::getOutputDirectory);
            compileIr.getOutputIrFile().set(irDir.map(dir -> dir.file(project.getName() + ".conjure.json")));
            compileIr.getProductDependencies().set(project.provider(pdepsExtension::getProductDependencies));
            compileIr.dependsOn(copyConjureSourcesTask);
            compileIr.dependsOn(extractCompilerTask);
        });
    }

    private static TaskProvider<Copy> createCopyConjureSourceTask(Project project, SourceDirectorySet sourceset) {
        Provider<Directory> buildDir = project.getLayout().getBuildDirectory().dir("conjure");
        TaskProvider<Copy> copyConjureSourcesTask = project.getTasks()
                .register("copyConjureSourcesIntoBuild", Copy.class, task -> {
                    task.into(buildDir).from(sourceset);

                    // Replacing this with a lambda is not supported for build caching
                    // (see https://github.com/gradle/gradle/issues/5510)
                    task.doFirst(new Action<Task>() {
                        @Override
                        public void execute(Task _task) {
                            GFileUtils.deleteDirectory(buildDir.get().getAsFile());
                        }
                    });
                });

        project.getTasks()
                .getByName(LifecycleBasePlugin.CLEAN_TASK_NAME)
                .dependsOn(project.getTasks().findByName("cleanCopyConjureSourcesIntoBuild"));

        return copyConjureSourcesTask;
    }

    private static void createServiceDependenciesTask(Project project, ConjureProductDependenciesExtension ext) {
        project.getTasks().register(SERVICE_DEPENDENCIES_TASK, GenerateConjureServiceDependenciesTask.class, task -> {
            task.setConjureServiceDependencies(ext::getProductDependencies);
        });
    }
}
