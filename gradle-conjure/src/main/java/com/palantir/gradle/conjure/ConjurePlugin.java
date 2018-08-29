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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.conjure.api.ConjureExtension;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import com.palantir.gradle.conjure.compat.NewTaskProvider;
import com.palantir.gradle.conjure.compat.OldTaskProvider;
import com.palantir.gradle.conjure.compat.TaskProvider;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.Action;
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

    public static final String CONJURE_IR = "compileIr";

    // configuration names
    private static final String CONJURE_COMPILER = "conjureCompiler";
    private static final String CONJURE_TYPESCRIPT = "conjureTypeScript";
    private static final String CONJURE_PYTHON = "conjurePython";
    private static final String CONJURE_JAVA = "conjureJava";

    // executable distributions
    private static final String CONJURE_COMPILER_BINARY = "com.palantir.conjure:conjure";
    private static final String CONJURE_JAVA_BINARY = "com.palantir.conjure.java:conjure-java";
    private static final String CONJURE_TYPESCRIPT_BINARY = "com.palantir.conjure.typescript:conjure-typescript@tgz";
    private static final String CONJURE_PYTHON_BINARY = "com.palantir.conjure.python:conjure-python";

    // java project constants
    private static final String JAVA_OBJECTS_SUFFIX = "-objects";
    private static final String JAVA_JERSEY_SUFFIX = "-jersey";
    private static final String JAVA_RETROFIT_SUFFIX = "-retrofit";
    private static final String JAVA_GENERATED_SOURCE_DIRNAME = "src/generated/java";
    private static final String JAVA_GITIGNORE_CONTENTS = "/src/generated/java/\n";
    private static final Pattern GRADLE_MAJOR_MINOR = Pattern.compile("(\\d+)\\.(\\d+)\\b.*");

    private final SourceDirectorySetFactory sourceDirectorySetFactory;
    private Supplier<Optional<GradleVersion>> gradleVersion;

    @Inject
    public ConjurePlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
    }

    @Override
    public void apply(Project project) {
        this.gradleVersion = Suppliers.memoize(() -> getGradleVersion(project));

        project.getPlugins().apply(BasePlugin.class);
        ConjureExtension extension = project.getExtensions()
                .create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);

        // Set up conjure compile task
        TaskProvider compileConjure =
                registerTask(project, "compileConjure", DefaultTask.class, task -> {
                    task.setDescription("Generates code for your API definitions in src/main/conjure/**/*.yml");
                    task.setGroup(TASK_GROUP);
                });
        applyDependencyForIdeTasks(project, compileConjure);

        TaskProvider<Copy> copyConjureSourcesTask = getConjureSources(project);
        TaskProvider<CompileIrTask> compileIrTask = createCompileIrTask(project, copyConjureSourcesTask);

        setupConjureJavaProject(project, extension::getJava, compileConjure, compileIrTask);
        setupConjurePythonProject(project, extension::getPython, compileConjure, compileIrTask);
        setupConjureTypescriptProject(project, extension::getTypescript, compileConjure, compileIrTask);
    }

    private void setupConjureJavaProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            TaskProvider compileConjure,
            TaskProvider<CompileIrTask> compileIrTask) {

        Set<String> javaProjectSuffixes = ImmutableSet.of(
                JAVA_OBJECTS_SUFFIX, JAVA_JERSEY_SUFFIX, JAVA_RETROFIT_SUFFIX);
        if (javaProjectSuffixes.stream().anyMatch(suffix -> project.findProject(project.getName() + suffix) != null)) {
            Configuration conjureJavaConfig = project.getConfigurations().maybeCreate(CONJURE_JAVA);
            File conjureJavaDir = new File(project.getBuildDir(), CONJURE_JAVA);
            project.getDependencies().add(CONJURE_JAVA, CONJURE_JAVA_BINARY);
            TaskProvider<ExtractExecutableTask> extractJavaTask = createExtractTask(
                    project, "extractConjureJava", conjureJavaConfig, conjureJavaDir, "conjure-java");

            setupConjureObjectsProject(
                    project, optionsSupplier, compileConjure, compileIrTask, extractJavaTask);
            setupConjureRetrofitProject(
                    project, optionsSupplier, compileConjure, compileIrTask, extractJavaTask);
            setupConjureJerseyProject(
                    project, optionsSupplier, compileConjure, compileIrTask, extractJavaTask);
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

    private void setupConjureObjectsProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            TaskProvider<DefaultTask> compileConjure,
            TaskProvider<CompileIrTask> compileIrTask,
            TaskProvider<ExtractExecutableTask> extractJavaTask) {

        String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
        if (project.findProject(objectsProjectName) != null) {
            project.project(objectsProjectName, (subproj) -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                TaskProvider<ConjureGeneratorTask> conjureGeneratorTask =
                        registerTask(project, "compileConjureObjects", ConjureGeneratorTask.class, task -> {
                            task.setDescription("Generates Java POJOs from your Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.setExecutablePath(extractJavaTask.get()::getExecutable);
                            task.setOptions(() -> addFlag(optionsSupplier.get(), "objects"));
                            task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(compileIrTask);

                            task.dependsOn(createWriteGitignoreTask(
                                    subproj,
                                    "gitignoreConjureObjects",
                                    subproj.getProjectDir(),
                                    JAVA_GITIGNORE_CONTENTS));
                            task.dependsOn(extractJavaTask);
                        });
                subproj.getTasks().named("compileJava").configure(cj -> cj.dependsOn(conjureGeneratorTask));
                compileConjure.configure(cc -> cc.dependsOn(conjureGeneratorTask));
                applyDependencyForIdeTasks(subproj, conjureGeneratorTask);

                project.getTasks().named(TASK_CLEAN).configure(cleanTask -> {
                    cleanTask.dependsOn("cleanCompileConjureObjects");
                });
                subproj.getDependencies().add("compile", "com.palantir.conjure.java:conjure-lib");
            });
        }
    }

    private void setupConjureRetrofitProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            TaskProvider<DefaultTask> compileConjure,
            TaskProvider<CompileIrTask> compileIrTask,
            TaskProvider<ExtractExecutableTask> extractJavaTask) {

        String retrofitProjectName = project.getName() + JAVA_RETROFIT_SUFFIX;
        if (project.findProject(retrofitProjectName) != null) {
            String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
            Project objectsProject = project.findProject(objectsProjectName);
            if (objectsProject == null) {
                throw new IllegalStateException(
                        String.format("Cannot enable '%s' without '%s'", retrofitProjectName, objectsProjectName));
            }

            project.project(retrofitProjectName, (subproj) -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                TaskProvider conjureGeneratorTask =
                        registerTask(project, "compileConjureRetrofit", ConjureGeneratorTask.class, task -> {
                            task.setDescription("Generates Retrofit interfaces for use on the client-side from your "
                                    + "Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.setExecutablePath(extractJavaTask.get()::getExecutable);
                            task.setOptions(() -> addFlag(optionsSupplier.get(), "retrofit"));
                            task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(compileIrTask);

                            task.dependsOn(createWriteGitignoreTask(
                                    subproj,
                                    "gitignoreConjureRetrofit",
                                    subproj.getProjectDir(),
                                    JAVA_GITIGNORE_CONTENTS));
                            task.dependsOn(extractJavaTask);
                        });
                subproj.getTasks().named("compileJava").configure(cj -> cj.dependsOn(conjureGeneratorTask));
                compileConjure.configure(cc -> cc.dependsOn(conjureGeneratorTask));
                applyDependencyForIdeTasks(subproj, conjureGeneratorTask);

                project.getTasks().named(TASK_CLEAN).configure(cleanTask -> {
                    cleanTask.dependsOn("cleanCompileConjureRetrofit");
                });
                subproj.getDependencies().add("compile", objectsProject);
                subproj.getDependencies().add("compile", "com.squareup.retrofit2:retrofit");
            });
        }
    }

    private void setupConjureJerseyProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            TaskProvider<DefaultTask> compileConjure,
            TaskProvider<CompileIrTask> compileIrTask,
            TaskProvider<ExtractExecutableTask> extractJavaTask) {

        String jerseyProjectName = project.getName() + JAVA_JERSEY_SUFFIX;
        if (project.findProject(jerseyProjectName) != null) {
            String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
            Project objectProject = project.findProject(objectsProjectName);
            if (objectProject == null) {
                throw new IllegalStateException(
                        String.format("Cannot enable '%s' without '%s'", jerseyProjectName, objectsProjectName));
            }

            project.project(jerseyProjectName, (subproj) -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                TaskProvider conjureGeneratorTask =
                        registerTask(project, "compileConjureJersey", ConjureGeneratorTask.class, task -> {
                            task.setDescription("Generates Jersey interfaces from your Conjure definitions "
                                    + "(for use on both the client-side and server-side).");
                            task.setGroup(TASK_GROUP);
                            task.setExecutablePath(extractJavaTask.get()::getExecutable);
                            task.setOptions(() -> addFlag(optionsSupplier.get(), "jersey"));
                            task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(compileIrTask);

                            task.dependsOn(createWriteGitignoreTask(
                                    subproj,
                                    "gitignoreConjureJersey",
                                    subproj.getProjectDir(),
                                    JAVA_GITIGNORE_CONTENTS));
                            task.dependsOn(extractJavaTask);
                        });
                getNamedTask(subproj, "compileJava").configure(cj -> cj.dependsOn(conjureGeneratorTask));
                compileConjure.configure(cc -> cc.dependsOn(conjureGeneratorTask));
                applyDependencyForIdeTasks(subproj, conjureGeneratorTask);

                getNamedTask(project, TASK_CLEAN).configure(cleanTask -> {
                    cleanTask.dependsOn("cleanCompileConjureJersey");
                });
                subproj.getDependencies().add("compile", objectProject);
                subproj.getDependencies().add("compile", "javax.ws.rs:javax.ws.rs-api");
            });
        }
    }

    private void setupConjureTypescriptProject(
            Project project,
            Supplier<GeneratorOptions> options,
            TaskProvider<DefaultTask> compileConjure,
            TaskProvider<CompileIrTask> compileIrTask) {
        String typescriptProjectName = project.getName() + "-typescript";
        if (project.findProject(typescriptProjectName) != null) {
            Configuration conjureTypeScriptConfig = project.getConfigurations().maybeCreate(CONJURE_TYPESCRIPT);
            project.project(typescriptProjectName, (subproj) -> {
                applyDependencyForIdeTasks(subproj, compileConjure);
                File conjureTypescriptDir = new File(project.getBuildDir(), CONJURE_TYPESCRIPT);
                File srcDirectory = subproj.file("src");
                project.getDependencies().add("conjureTypeScript", CONJURE_TYPESCRIPT_BINARY);

                TaskProvider<ExtractExecutableTask> extractConjureTypeScriptTask = createExtractTask(
                        project,
                        "extractConjureTypeScript",
                        conjureTypeScriptConfig,
                        conjureTypescriptDir,
                        "conjure-typescript");
                TaskProvider<CompileConjureTypeScriptTask> compileConjureTypeScript = registerTask(
                        project, "compileConjureTypeScript", CompileConjureTypeScriptTask.class, task -> {
                            task.setDescription(
                                    "Generates TypeScript files and a package.json from your Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.setSource(compileIrTask);
                            task.setExecutablePath(extractConjureTypeScriptTask.get()::getExecutable);
                            task.setOutputDirectory(srcDirectory);
                            task.setOptions(options);
                            task.dependsOn(createWriteGitignoreTask(subproj,
                                    "gitignoreConjureTypeScript",
                                    subproj.getProjectDir(),
                                    "/src/\n"));
                            task.dependsOn(extractConjureTypeScriptTask);
                        });
                compileConjure.configure(cc -> cc.dependsOn(compileConjureTypeScript));

                TaskProvider<Exec> installTypeScriptDependencies =
                        registerTask(project, "installTypeScriptDependencies", Exec.class, task -> {
                            task.commandLine("npm", "install", "--no-package-lock");
                            task.workingDir(srcDirectory);
                            task.dependsOn(compileConjureTypeScript);
                            task.getInputs().file(new File(srcDirectory, "package.json"));
                            task.getOutputs().dir(new File(srcDirectory, "node_modules"));
                        });
                TaskProvider<Exec> compileTypeScript =
                        registerTask(project, "compileTypeScript", Exec.class, task -> {
                            task.setDescription(
                                    "Runs `npm tsc` to compile generated TypeScript files into JavaScript files.");
                            task.setGroup(TASK_GROUP);
                            task.commandLine("npm", "run-script", "build");
                            task.workingDir(srcDirectory);
                            task.dependsOn(installTypeScriptDependencies);
                        });
                TaskProvider<Exec> publishTypeScript =
                        registerTask(project, "publishTypeScript", Exec.class, task -> {
                            task.setDescription("Runs `npm publish` to publish a TypeScript package "
                                    + "generated from your Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.commandLine("npm", "publish");
                            task.workingDir(srcDirectory);
                            task.dependsOn(compileConjureTypeScript);
                            task.dependsOn(compileTypeScript);
                        });
                subproj.afterEvaluate(p -> subproj.getTasks().maybeCreate("publish").dependsOn(publishTypeScript));
                getNamedTask(project, TASK_CLEAN).configure(cleanTask -> {
                    cleanTask.dependsOn("cleanCompileConjureTypeScript");
                });
            });
        }
    }

    private void setupConjurePythonProject(
            Project project,
            Supplier<GeneratorOptions> options,
            TaskProvider<DefaultTask> compileConjure,
            TaskProvider<CompileIrTask> compileIrTask) {
        String pythonProjectName = project.getName() + "-python";
        if (project.findProject(pythonProjectName) != null) {
            Configuration conjurePythonConfig = project.getConfigurations().maybeCreate(CONJURE_PYTHON);

            project.project(pythonProjectName, (subproj) -> {
                applyDependencyForIdeTasks(subproj, compileConjure);
                File conjurePythonDir = new File(project.getBuildDir(), CONJURE_PYTHON);
                File buildDir = new File(project.getBuildDir(), "python");
                File distDir = new File(buildDir, "dist");
                project.getDependencies().add(CONJURE_PYTHON, CONJURE_PYTHON_BINARY);
                TaskProvider<ExtractExecutableTask> extractConjurePythonTask = createExtractTask(
                        project, "extractConjurePython", conjurePythonConfig, conjurePythonDir, "conjure-python");
                TaskProvider<CompileConjurePythonTask> compileConjurePython =
                        registerTask(project, "compileConjurePython", CompileConjurePythonTask.class, task -> {
                            task.setDescription("Generates Python files from your Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.setSource(compileIrTask);
                            task.setExecutablePath(extractConjurePythonTask.get()::getExecutable);
                            task.setOutputDirectory(subproj.file("python"));
                            task.setOptions(options);
                            task.dependsOn(createWriteGitignoreTask(subproj,
                                    "gitignoreConjurePython",
                                    subproj.getProjectDir(),
                                    "/python/\n"));
                            task.dependsOn(extractConjurePythonTask);
                        });
                compileConjure.configure(cc -> cc.dependsOn(compileConjurePython));
                project.getTasks().register("buildWheel", Exec.class, task -> {
                    task.setDescription("Runs `python setup.py sdist bdist_wheel --universal` to build a python wheel "
                            + "generated from your Conjure definitions.");
                    task.setGroup(TASK_GROUP);
                    task.commandLine("python", "setup.py", "build", "--build-base", buildDir, "egg_info", "--egg-base",
                            buildDir, "sdist", "--dist-dir", distDir, "bdist_wheel", "--universal", "--dist-dir",
                            distDir);
                    task.workingDir(subproj.file("python"));
                    task.dependsOn(compileConjurePython);
                    project.getTasks().named(TASK_CLEAN).configure(cleanTask -> {
                        cleanTask.dependsOn("cleanCompileConjurePython");
                    });
                });
            });
        }
    }

    private static void addGeneratedToMainSourceSet(Project subproj) {
        JavaPluginConvention javaPlugin = subproj.getConvention().findPlugin(JavaPluginConvention.class);
        javaPlugin.getSourceSets().getByName("main").getJava().srcDir(subproj.files(JAVA_GENERATED_SOURCE_DIRNAME));
    }

    private void applyDependencyForIdeTasks(Project project, TaskProvider dependency) {
        project.getPlugins().withType(IdeaPlugin.class, plugin -> {
            getNamedTask(project, "ideaModule").configure(task -> task.dependsOn(dependency));

            plugin.getModel().getModule().getSourceDirs().add(project.file(JAVA_GENERATED_SOURCE_DIRNAME));
            plugin.getModel().getModule().getGeneratedSourceDirs().add(project.file(JAVA_GENERATED_SOURCE_DIRNAME));
        });
        project.getPlugins().withType(EclipsePlugin.class, plugin -> {
            getNamedTask(project, "eclipseClasspath").configure(task -> {
                task.dependsOn(dependency);
            });
        });
    }

    private static Task createWriteGitignoreTask(Project project, String taskName, File outputDir, String contents) {
        WriteGitignoreTask writeGitignoreTask = project.getTasks().create(taskName, WriteGitignoreTask.class);
        writeGitignoreTask.setOutputDirectory(outputDir);
        writeGitignoreTask.setContents(contents);
        return writeGitignoreTask;
    }

    private TaskProvider<ExtractExecutableTask> createExtractTask(
            Project project, String taskName, Configuration config, File outputDir, String executableName) {
        return registerTask(project, taskName, ExtractExecutableTask.class, task -> {
            task.setArchive(config);
            task.setOutputDirectory(outputDir);
            task.setExecutableName(executableName);
        });
    }

    private TaskProvider<CompileIrTask> createCompileIrTask(
            Project project, TaskProvider<Copy> copyConjureSourcesTask) {
        Configuration conjureCompilerConfig = project.getConfigurations().maybeCreate(CONJURE_COMPILER);
        File conjureCompilerDir = new File(project.getBuildDir(), CONJURE_COMPILER);
        project.getDependencies().add(CONJURE_COMPILER, CONJURE_COMPILER_BINARY);
        TaskProvider<ExtractExecutableTask> extractCompilerTask = createExtractTask(
                project, "extractConjure", conjureCompilerConfig, conjureCompilerDir, "conjure");

        File irPath = Paths.get(
                project.getBuildDir().toString(), "conjure-ir", project.getName() + ".conjure.json").toFile();

        return registerTask(project, CONJURE_IR, CompileIrTask.class, compileIr -> {
            compileIr.setDescription("Converts your Conjure YML files into a single portable JSON file in IR format.");
            compileIr.setGroup(TASK_GROUP);
            compileIr.setInputDirectory(copyConjureSourcesTask.get()::getDestinationDir);
            compileIr.setExecutablePath(extractCompilerTask.get()::getExecutable);
            compileIr.setOutputFile(irPath);
            compileIr.dependsOn(copyConjureSourcesTask);
            compileIr.dependsOn(extractCompilerTask);
        });
    }

    private TaskProvider<Copy> getConjureSources(
            Project project, SourceDirectorySetFactory sourceDirectorySetFactory) {
        // Conjure code source set
        SourceDirectorySet conjureSourceSet = sourceDirectorySetFactory.create("conjure");
        conjureSourceSet.setSrcDirs(Collections.singleton("src/main/conjure"));
        conjureSourceSet.setIncludes(Collections.singleton("**/*.yml"));

        // Copy conjure imports into build directory
        File buildDir = new File(project.getBuildDir(), "conjure");

        // Copy conjure sources into build directory
        TaskProvider<Copy> copyConjureSourcesTask =
                registerTask(project, "copyConjureSourcesIntoBuild", Copy.class, copy -> {
                    copy.into(project.file(buildDir)).from(conjureSourceSet);
                    copy.doFirst(task -> GFileUtils.deleteDirectory(buildDir));
                });

        project.getTasks().named(TASK_CLEAN).configure(cleanTask -> {
            cleanTask.dependsOn("cleanCopyConjureSourcesIntoBuild");
        });

        return copyConjureSourcesTask;
    }

    private <T extends Task> TaskProvider<T> registerTask(
            Project project, String taskName, Class<T> taskType, Action<? super T> configuration) {
        if (isAtLeast(4, 9)) {
            return new NewTaskProvider<>(project.getTasks().register(taskName, taskType, configuration));
        } else {
            return new OldTaskProvider<>(project.getTasks().create(taskName, taskType, configuration));
        }
    }

    private TaskProvider<Task> getNamedTask(Project project, String taskName) {
        if (isAtLeast(4, 9)) {
            return new NewTaskProvider<>(project.getTasks().named(taskName));
        } else {
            return new OldTaskProvider<>(project.getTasks().getByName(taskName));
        }
    }

    private static Optional<GradleVersion> getGradleVersion(Project project) {
        Matcher matcher = GRADLE_MAJOR_MINOR.matcher(project.getGradle().getGradleVersion());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int major = Integer.parseUnsignedInt(matcher.group(1));
        int minor = Integer.parseUnsignedInt(matcher.group(2));
        return Optional.of(new GradleVersion(major, minor));
    }

    private boolean isAtLeast(int expectedMajor, int expectedMinor) {
        Optional<GradleVersion> versionOpt = this.gradleVersion.get();
        if (!versionOpt.isPresent()) {
            // Assume false
            return false;
        }
        GradleVersion version = versionOpt.get();
        return version.getMajor() > expectedMajor
                || (version.getMajor() == expectedMajor && version.getMinor() >= expectedMinor);
    }
}
