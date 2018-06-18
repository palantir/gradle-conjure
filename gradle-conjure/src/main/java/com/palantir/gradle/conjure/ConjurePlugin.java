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
import com.google.common.collect.Iterables;
import com.palantir.gradle.conjure.api.ConjureExtension;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Exec;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.util.GFileUtils;

public final class ConjurePlugin implements Plugin<Project> {

    private static final String TASK_GROUP = "Conjure";
    private static final String TASK_CLEAN = "clean";

    // configuration names
    private static final String CONJURE_TYPESCRIPT = "conjureTypeScript";
    private static final String CONJURE_PYTHON = "conjurePython";
    private static final String CONJURE_JAVA = "conjureJava";

    // executable distributions
    private static final String CONJURE_JAVA_BINARY = "com.palantir.conjure.java:conjure-java";
    private static final String CONJURE_TYPESCRIPT_BINARY = "com.palantir.conjure.typescript:conjure-typescript@tgz";
    private static final String CONJURE_PYTHON_BINARY = "com.palantir.conjure.python:conjure-python";

    // java project constants
    private static final String JAVA_OBJECTS_SUFFIX = "-objects";
    private static final String JAVA_JERSEY_SUFFIX = "-jersey";
    private static final String JAVA_RETROFIT_SUFFIX = "-retrofit";
    private static final String JAVA_GENERATED_SOURCE_DIRNAME = "src/generated/java";
    private static final String JAVA_GITIGNORE_DIRNAME = "src";
    private static final String JAVA_GITIGNORE_CONTENTS = "/generated/**/*.java\n";

    private final SourceDirectorySetFactory sourceDirectorySetFactory;

    @Inject
    public ConjurePlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
    }

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);
        ConjureExtension extension = project.getExtensions()
                .create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        // Set up conjure compile task
        Task compileConjure = project.getTasks().create("compileConjure", DefaultTask.class);
        compileConjure.setDescription("Generates code for your API definitions in src/main/conjure/**/*.yml");
        compileConjure.setGroup(TASK_GROUP);
        applyDependencyForIdeTasks(project, compileConjure);

        Copy copyConjureSourcesTask = getConjureSources(project, sourceDirectorySetFactory);
        Task compileIrTask = createCompileIrTask(project, copyConjureSourcesTask);

        setupConjureJavaProject(project, extension::getJava, compileConjure, compileIrTask);
        setupConjurePythonProject(project, compileConjure, compileIrTask);
        setupConjureTypescriptProject(project, extension::getTypescript, compileConjure, compileIrTask);
    }

    private static void setupConjureJavaProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask) {

        Set<String> javaProjectSuffixes = ImmutableSet.of(
                JAVA_OBJECTS_SUFFIX, JAVA_JERSEY_SUFFIX, JAVA_RETROFIT_SUFFIX);
        if (javaProjectSuffixes.stream().anyMatch(suffix -> project.findProject(project.getName() + suffix) != null)) {
            final Configuration conjureJavaConfig =
                    project.getConfigurations().findByName(CONJURE_JAVA) != null
                            ? project.getConfigurations().findByName(CONJURE_JAVA)
                            : project.getConfigurations().create(CONJURE_JAVA);
            File conjureJavaDir = new File(project.getBuildDir(), CONJURE_JAVA);
            project.getDependencies().add(CONJURE_JAVA, CONJURE_JAVA_BINARY);
            Task extractJavaTask = createExtractTask(
                    project, "extractConjureJava", conjureJavaConfig, conjureJavaDir);

            File javaExecutablePath = extractExecutable(conjureJavaDir, "java", conjureJavaConfig);
            setupConjureObjectsProject(
                    project, javaExecutablePath, optionsSupplier, compileConjure, compileIrTask, extractJavaTask);
            setupConjureRetrofitProject(
                    project, javaExecutablePath, optionsSupplier, compileConjure, compileIrTask, extractJavaTask);
            setupConjureJerseyProject(
                    project, javaExecutablePath, optionsSupplier, compileConjure, compileIrTask, extractJavaTask);
        }
    }

    private static GeneratorOptions addFlag(GeneratorOptions options, String flag) {
        Preconditions.checkArgument(
                !options.has(flag),
                "Passed GeneratorOptions already has flag '%s' set: %s", flag, options);
        GeneratorOptions generatorOptions = new GeneratorOptions(options);
        generatorOptions.setProperty(flag, true);
        return generatorOptions;
    }

    private static void setupConjureObjectsProject(
            Project project,
            File executablePath,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask,
            Task extractJavaTask) {

        String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
        if (project.findProject(objectsProjectName) != null) {
            project.project(objectsProjectName, (subproj) -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                project.getTasks().create(
                        "compileConjureObjects",
                        ConjureGeneratorTask.class,
                        (task) -> {
                            task.setExecutablePath(executablePath);
                            task.setOptions(() -> addFlag(optionsSupplier.get(), "objects"));
                            task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(compileIrTask);

                            compileConjure.dependsOn(task);
                            subproj.getTasks().getByName("compileJava").dependsOn(task);
                            applyDependencyForIdeTasks(subproj, task);
                            task.dependsOn(
                                    createWriteGitignoreTask(
                                            subproj,
                                            "gitignoreConjureObjects",
                                            subproj.file(JAVA_GITIGNORE_DIRNAME),
                                            JAVA_GITIGNORE_CONTENTS));
                            task.dependsOn(extractJavaTask);
                        });

                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjureObjects"));
                subproj.getDependencies().add("compile", "com.palantir.conjure.java:conjure-lib");
            });
        }
    }

    private static void setupConjureRetrofitProject(
            Project project,
            File executablePath,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask,
            Task extractJavaTask) {

        String retrofitProjectName = project.getName() + JAVA_RETROFIT_SUFFIX;
        if (project.findProject(retrofitProjectName) != null) {
            String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
            if (project.findProject(objectsProjectName) == null) {
                throw new IllegalStateException(
                        String.format("Cannot enable '%s' without '%s'", retrofitProjectName, objectsProjectName));
            }

            project.project(retrofitProjectName, (subproj) -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                project.getTasks().create(
                        "compileConjureRetrofit",
                        ConjureGeneratorTask.class,
                        (task) -> {
                            task.setExecutablePath(executablePath);
                            task.setOptions(() -> addFlag(optionsSupplier.get(), "retrofit"));
                            task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(compileIrTask);

                            compileConjure.dependsOn(task);
                            subproj.getTasks().getByName("compileJava").dependsOn(task);
                            applyDependencyForIdeTasks(subproj, task);
                            task.dependsOn(
                                    createWriteGitignoreTask(
                                            subproj,
                                            "gitignoreConjureRetrofit",
                                            subproj.file(JAVA_GITIGNORE_DIRNAME),
                                            JAVA_GITIGNORE_CONTENTS));
                            task.dependsOn(extractJavaTask);
                        });

                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjureRetrofit"));
                subproj.getDependencies().add("compile", project.findProject(objectsProjectName));
                subproj.getDependencies().add("compile", "com.squareup.retrofit2:retrofit");
            });
        }
    }

    private static void setupConjureJerseyProject(
            Project project,
            File executablePath,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask,
            Task extractJavaTask) {

        String jerseyProjectName = project.getName() + JAVA_JERSEY_SUFFIX;
        if (project.findProject(jerseyProjectName) != null) {
            String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
            if (project.findProject(objectsProjectName) == null) {
                throw new IllegalStateException(
                        String.format("Cannot enable '%s' without '%s'", jerseyProjectName, objectsProjectName));
            }

            project.project(jerseyProjectName, (subproj) -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                project.getTasks().create(
                        "compileConjureJersey",
                        ConjureGeneratorTask.class,
                        (task) -> {
                            task.setExecutablePath(executablePath);
                            task.setOptions(() -> addFlag(optionsSupplier.get(), "jersey"));
                            task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(compileIrTask);

                            compileConjure.dependsOn(task);
                            subproj.getTasks().getByName("compileJava").dependsOn(task);
                            applyDependencyForIdeTasks(subproj, task);
                            task.dependsOn(
                                    createWriteGitignoreTask(
                                            subproj,
                                            "gitignoreConjureJersey",
                                            subproj.file(JAVA_GITIGNORE_DIRNAME),
                                            JAVA_GITIGNORE_CONTENTS));
                            task.dependsOn(extractJavaTask);
                        });

                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjureJersey"));
                subproj.getDependencies().add("compile", project.findProject(objectsProjectName));
                subproj.getDependencies().add("compile", "javax.ws.rs:javax.ws.rs-api");
            });
        }
    }

    private static void setupConjureTypescriptProject(
            Project project,
            Supplier<GeneratorOptions> options,
            Task compileConjure,
            Task compileIrTask) {
        String typescriptProjectName = project.getName() + "-typescript";
        if (project.findProject(typescriptProjectName) != null) {
            final Configuration conjureTypeScriptConfig =
                    project.getConfigurations().findByName(CONJURE_TYPESCRIPT) != null
                            ? project.getConfigurations().findByName(CONJURE_TYPESCRIPT)
                            : project.getConfigurations().create(CONJURE_TYPESCRIPT);
            project.project(typescriptProjectName, (subproj) -> {
                applyDependencyForIdeTasks(subproj, compileConjure);
                File conjureTypescriptDir = new File(project.getBuildDir(), CONJURE_TYPESCRIPT);
                File srcDirectory = subproj.file("src");
                project.getDependencies().add("conjureTypeScript", CONJURE_TYPESCRIPT_BINARY);

                Task extractConjureTypeScriptTask = createExtractTask(
                        project, "extractConjureTypeScript", conjureTypeScriptConfig, conjureTypescriptDir);
                Task compileConjureTypeScript = project.getTasks().create("compileConjureTypeScript",
                        CompileConjureTypeScriptTask.class, (task) -> {
                            task.setSource(compileIrTask);
                            task.setExecutablePath(
                                    extractExecutable(conjureTypescriptDir, "typescript", conjureTypeScriptConfig));
                            task.setOutputDirectory(srcDirectory);
                            task.setOptions(options);
                            compileConjure.dependsOn(task);
                            task.dependsOn(
                                    createWriteGitignoreTask(
                                            subproj, "gitignoreConjureTypeScript", subproj.getProjectDir(),
                                            "*.js\n*.ts\n.npmrc\npackage.json\ntsconfig.json\nnode_modules\n"));
                            task.dependsOn(extractConjureTypeScriptTask);
                        });

                Task installTypeScriptDependencies = project.getTasks().create("installTypeScriptDependencies",
                        Exec.class, task -> {
                            task.commandLine("npm", "install", "--no-package-lock");
                            task.workingDir(srcDirectory);
                            task.dependsOn(compileConjureTypeScript);
                            task.getInputs().file(new File(srcDirectory, "package.json"));
                            task.getOutputs().dir(new File(srcDirectory, "node_modules"));
                        });
                Task compileTypeScript = project.getTasks().create("compileTypeScript", Exec.class, task -> {
                    task.commandLine("npm", "run-script", "build");
                    task.workingDir(srcDirectory);
                    task.dependsOn(installTypeScriptDependencies);
                });
                Task publishTypeScript = project.getTasks().create("publishTypeScript", Exec.class, task -> {
                    task.commandLine("npm", "publish");
                    task.workingDir(srcDirectory);
                    task.dependsOn(compileConjureTypeScript);
                    task.dependsOn(compileTypeScript);
                });
                subproj.afterEvaluate(p -> subproj.getTasks().maybeCreate("publish").dependsOn(publishTypeScript));
                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjureTypeScript"));
            });
        }
    }

    private static void setupConjurePythonProject(
            Project project,
            Task compileConjure,
            Task compileIrTask) {
        String pythonProjectName = project.getName() + "-python";
        if (project.findProject(pythonProjectName) != null) {
            final Configuration conjurePythonConfig =
                    project.getConfigurations().findByName(CONJURE_PYTHON) != null
                            ? project.getConfigurations().findByName(CONJURE_PYTHON)
                            : project.getConfigurations().create(CONJURE_PYTHON);
            project.project(pythonProjectName, (subproj) -> {
                applyDependencyForIdeTasks(subproj, compileConjure);
                File conjurePythonDir = new File(project.getBuildDir(), CONJURE_PYTHON);
                project.getDependencies().add(CONJURE_PYTHON, CONJURE_PYTHON_BINARY);

                Task extractConjurePythonTask = createExtractTask(
                        project, "extractConjurePython", conjurePythonConfig, conjurePythonDir);
                project.getTasks().create("compileConjurePython",
                        ConjureGeneratorTask.class,
                        (task) -> {
                            task.setSource(compileIrTask);
                            task.setExecutablePath(extractExecutable(conjurePythonDir, "python", conjurePythonConfig));
                            task.setOutputDirectory(subproj.file("python"));
                            compileConjure.dependsOn(task);
                            task.dependsOn(
                                    createWriteGitignoreTask(
                                            subproj, "gitignoreConjurePython", subproj.getProjectDir(),
                                            "*.py\n"));
                            task.dependsOn(extractConjurePythonTask);
                        });

                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjurePython"));
            });
        }
    }

    private static void addGeneratedToMainSourceSet(Project subproj) {
        JavaPluginConvention javaPlugin = subproj.getConvention().findPlugin(JavaPluginConvention.class);
        javaPlugin.getSourceSets().getByName("main").getJava().srcDir(subproj.files(JAVA_GENERATED_SOURCE_DIRNAME));
    }

    private static void applyDependencyForIdeTasks(Project project, Task compileConjure) {
        project.getPlugins().withType(IdeaPlugin.class, plugin -> {
            Task task = project.getTasks().findByName("ideaModule");
            if (task != null) {
                task.dependsOn(compileConjure);
            }

            plugin.getModel().getModule().getSourceDirs().add(project.file(JAVA_GENERATED_SOURCE_DIRNAME));
            plugin.getModel().getModule().getGeneratedSourceDirs().add(project.file(JAVA_GENERATED_SOURCE_DIRNAME));
        });
        project.getPlugins().withType(EclipsePlugin.class, plugin -> {
            Task task = project.getTasks().findByName("eclipseClasspath");
            if (task != null) {
                task.dependsOn(compileConjure);
            }
        });
    }

    private static Task createWriteGitignoreTask(Project project, String taskName, File outputDir, String contents) {
        WriteGitignoreTask writeGitignoreTask = project.getTasks().create(taskName, WriteGitignoreTask.class);
        writeGitignoreTask.setOutputDirectory(outputDir);
        writeGitignoreTask.setContents(contents);
        return writeGitignoreTask;
    }

    private static Task createExtractTask(Project project, String taskName, Configuration config, File output) {
        return project.getTasks().create(
                taskName,
                Copy.class, (task) -> {
                    Set<File> resolvedFiles = config.resolve();
                    Preconditions.checkState(resolvedFiles.size() == 1,
                            "Expected exactly one %s dependency, found %s",
                            taskName,
                            resolvedFiles);
                    task.into(output);
                    task.from(project.tarTree(Iterables.getOnlyElement(resolvedFiles)));
                });
    }

    private static Task createCompileIrTask(Project project, Copy copyConjureSourcesTask) {
        File irPath = Paths.get(project.getBuildDir().toString(), "conjure-ir", project.getName() + ".json").toFile();
        return project.getTasks().create("compileIr", CompileIrTask.class, compileIr -> {
            compileIr.setDescription("Converts your Conjure YML files into a single portable file containing IR JSON.");
            compileIr.setDescription(TASK_GROUP);
            compileIr.setSource(copyConjureSourcesTask);
            compileIr.setOutputFile(irPath);
        });
    }

    private static Copy getConjureSources(Project project, SourceDirectorySetFactory sourceDirectorySetFactory) {
        // Conjure code source set
        SourceDirectorySet conjureSourceSet = sourceDirectorySetFactory.create("conjure");
        conjureSourceSet.setSrcDirs(Collections.singleton("src/main/conjure"));
        conjureSourceSet.setIncludes(Collections.singleton("**/*.yml"));

        // Copy conjure imports into build directory
        File buildDir = new File(project.getBuildDir(), "conjure");

        // Copy conjure sources into build directory
        Copy copyConjureSourcesTask = project.getTasks().create("copyConjureSourcesIntoBuild", Copy.class);
        copyConjureSourcesTask.into(project.file(buildDir)).from(conjureSourceSet);

        copyConjureSourcesTask.doFirst(task -> {
            GFileUtils.deleteDirectory(buildDir);
        });

        Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
        cleanTask.dependsOn(project.getTasks().findByName("cleanCopyConjureSourcesIntoBuild"));

        return copyConjureSourcesTask;
    }

    private static File extractExecutable(File rootDir, String language, Configuration configuration) {
        Set<File> configurationFiles = configuration.resolve();
        Preconditions.checkState(configurationFiles.size() == 1,
                "Expected exactly one dependency, found %s", configurationFiles);
        return new File(
                rootDir,
                String.format(
                        "%s/bin/conjure-%s",
                        Iterables.getOnlyElement(configurationFiles).getName().replaceAll(".tgz", ""),
                        language));

    }
}
