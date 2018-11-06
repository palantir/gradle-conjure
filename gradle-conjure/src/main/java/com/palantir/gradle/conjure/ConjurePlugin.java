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
import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Exec;
import org.gradle.jvm.tasks.Jar;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.util.GFileUtils;

public final class ConjurePlugin implements Plugin<Project> {

    static final String TASK_GROUP = "Conjure";
    static final String TASK_CLEAN = "clean";

    public static final String CONJURE_IR = "compileIr";

    // configuration names
    static final String CONJURE_COMPILER = "conjureCompiler";
    static final String CONJURE_TYPESCRIPT = "conjureTypeScript";
    static final String CONJURE_PYTHON = "conjurePython";
    static final String CONJURE_JAVA = "conjureJava";
    static final String CONJURE_JAVA_LIB = "conjureJavaLib";
    static final String CONJURE_JAVA_LIB_COMPILE_ONLY = "conjureJavaLibCompileOnly";

    private static final String CONJURE_JAVA_DEFAULT_VERSION = "2.4.0";

    // executable distributions
    private static final String CONJURE_COMPILER_BINARY = "com.palantir.conjure:conjure:4.3.0";
    private static final String CONJURE_JAVA_BINARY =
            "com.palantir.conjure.java:conjure-java:" + CONJURE_JAVA_DEFAULT_VERSION;
    static final String CONJURE_TYPESCRIPT_BINARY = "com.palantir.conjure.typescript:conjure-typescript:3.3.0@tgz";
    static final String CONJURE_PYTHON_BINARY = "com.palantir.conjure.python:conjure-python:3.9.1";

    // java project constants
    static final String JAVA_OBJECTS_SUFFIX = "-objects";
    static final String JAVA_JERSEY_SUFFIX = "-jersey";
    static final String JAVA_RETROFIT_SUFFIX = "-retrofit";
    static final String JAVA_GENERATED_SOURCE_DIRNAME = "src/generated/java";
    static final String JAVA_GITIGNORE_CONTENTS = "/src/generated/java/\n";

    private static final String JAX_RS_API_DEP = "javax.ws.rs:javax.ws.rs-api:2.0.1";
    private static final String JAVAX_ANNOTATION_API_DEP = "javax.annotation:javax.annotation-api:1.3.2";
    private static final String CONJURE_JAVA_LIB_DEP =
            "com.palantir.conjure.java:conjure-lib:" + CONJURE_JAVA_DEFAULT_VERSION;
    private static final String RETROFIT_2_DEP = "com.squareup.retrofit2:retrofit:2.1.0";


    private final org.gradle.api.internal.file.SourceDirectorySetFactory sourceDirectorySetFactory;

    @Inject
    public ConjurePlugin(org.gradle.api.internal.file.SourceDirectorySetFactory sourceDirectorySetFactory) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
    }

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);
        ConjureExtension conjureExtension = project.getExtensions()
                .create(ConjureExtension.EXTENSION_NAME, ConjureExtension.class);
        ConjureProductDependenciesExtension conjureProductDependenciesExtension = project.getExtensions()
                .create(ConjureProductDependenciesExtension.EXTENSION_NAME, ConjureProductDependenciesExtension.class);

        // Set up conjure compile task
        Task compileConjure = project.getTasks().create("compileConjure", DefaultTask.class);
        compileConjure.setDescription("Generates code for your API definitions in src/main/conjure/**/*.yml");
        compileConjure.setGroup(TASK_GROUP);
        applyDependencyForIdeTasks(project, compileConjure);

        Copy copyConjureSourcesTask = getConjureSources(project, sourceDirectorySetFactory);
        Task compileIrTask = createCompileIrTask(project, copyConjureSourcesTask);
        GenerateConjureServiceDependenciesTask productDependencyTask = project.getTasks().create(
                "generateConjureServiceDependencies", GenerateConjureServiceDependenciesTask.class, task -> {
                    task.setConjureServiceDependencies(conjureProductDependenciesExtension::getProductDependencies);
                });

        setupConjureJavaProject(
                project,
                immutableOptionsSupplier(conjureExtension::getJava),
                compileConjure,
                compileIrTask,
                productDependencyTask);
        setupConjurePythonProject(
                project,
                immutableOptionsSupplier(conjureExtension::getPython),
                compileConjure,
                compileIrTask);
        setupConjureTypescriptProject(
                project,
                immutableOptionsSupplier(conjureExtension::getTypescript),
                compileConjure,
                compileIrTask,
                productDependencyTask);
    }

    private static void setupConjureJavaProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask,
            GenerateConjureServiceDependenciesTask productDependencyTask) {
        Set<String> javaProjectSuffixes = ImmutableSet.of(
                JAVA_OBJECTS_SUFFIX, JAVA_JERSEY_SUFFIX, JAVA_RETROFIT_SUFFIX);
        if (javaProjectSuffixes.stream().anyMatch(suffix -> project.findProject(project.getName() + suffix) != null)) {
            Configuration conjureJavaConfig = project.getConfigurations().maybeCreate(CONJURE_JAVA);
            File conjureJavaDir = new File(project.getBuildDir(), CONJURE_JAVA);
            conjureJavaConfig.defaultDependencies(
                    deps -> deps.add(project.getDependencies().create(CONJURE_JAVA_BINARY)));
            ExtractExecutableTask extractJavaTask = ExtractExecutableTask.createExtractTask(
                    project, "extractConjureJava", conjureJavaConfig, conjureJavaDir, "conjure-java");

            setupConjureObjectsProject(
                    project,
                    optionsSupplier,
                    compileConjure,
                    compileIrTask,
                    extractJavaTask);
            setupConjureRetrofitProject(
                    project,
                    optionsSupplier,
                    compileConjure,
                    compileIrTask,
                    productDependencyTask,
                    extractJavaTask);
            setupConjureJerseyProject(
                    project,
                    optionsSupplier,
                    compileConjure,
                    compileIrTask,
                    productDependencyTask,
                    extractJavaTask);
        }
    }

    private static void setupConjureObjectsProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask,
            ExtractExecutableTask extractJavaTask) {

        String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
        if (project.findProject(objectsProjectName) != null) {
            project.project(objectsProjectName, subproj -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                project.getTasks().create(
                        "compileConjureObjects",
                        ConjureGeneratorTask.class,
                        task -> {
                            task.setDescription("Generates Java POJOs from your Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.setExecutablePath(extractJavaTask::getExecutable);
                            task.setOptions(() -> optionsSupplier.get().addFlag("objects"));
                            task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                            task.setSource(compileIrTask);

                            compileConjure.dependsOn(task);
                            subproj.getTasks().getByName("compileJava").dependsOn(task);
                            applyDependencyForIdeTasks(subproj, task);
                            task.dependsOn(
                                    createWriteGitignoreTask(
                                            subproj,
                                            "gitignoreConjureObjects",
                                            subproj.getProjectDir(),
                                            JAVA_GITIGNORE_CONTENTS));
                            task.dependsOn(extractJavaTask);
                        });
                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjureObjects"));

                configureJavaSubprojectDependencies(subproj);
            });
        }
    }

    private static void setupConjureRetrofitProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask,
            GenerateConjureServiceDependenciesTask productDependencyTask,
            ExtractExecutableTask extractJavaTask) {

        String retrofitProjectName = project.getName() + JAVA_RETROFIT_SUFFIX;
        if (project.findProject(retrofitProjectName) != null) {
            String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
            if (project.findProject(objectsProjectName) == null) {
                throw new IllegalStateException(
                        String.format("Cannot enable '%s' without '%s'", retrofitProjectName, objectsProjectName));
            }

            project.project(retrofitProjectName, subproj -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                project.getTasks().create("compileConjureRetrofit", ConjureGeneratorTask.class, task -> {
                    task.setDescription(
                            "Generates Retrofit interfaces for use on the client-side from your Conjure definitions.");
                    task.setGroup(TASK_GROUP);
                    task.setExecutablePath(extractJavaTask::getExecutable);
                    task.setOptions(() -> optionsSupplier.get().addFlag("retrofit"));
                    task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                    task.setSource(compileIrTask);

                    compileConjure.dependsOn(task);
                    subproj.getTasks().getByName("compileJava").dependsOn(task);
                    applyDependencyForIdeTasks(subproj, task);
                    task.dependsOn(createWriteGitignoreTask(
                            subproj,
                            "gitignoreConjureRetrofit",
                            subproj.getProjectDir(),
                            JAVA_GITIGNORE_CONTENTS));
                    task.dependsOn(extractJavaTask);
                    task.dependsOn(productDependencyTask);
                });

                compileConjure.dependsOn(createJavaProductDependenciesTask(
                        project,
                        subproj,
                        "conjureRetrofitProductDependency",
                        productDependencyTask));
                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjureRetrofit"));
                configureJavaSubprojectDependencies(subproj, RETROFIT_2_DEP);

                subproj.getDependencies().add("compile", project.findProject(objectsProjectName));
            });
        }
    }

    private static void setupConjureJerseyProject(
            Project project,
            Supplier<GeneratorOptions> optionsSupplier,
            Task compileConjure,
            Task compileIrTask,
            GenerateConjureServiceDependenciesTask productDependencyTask,
            ExtractExecutableTask extractJavaTask) {

        String jerseyProjectName = project.getName() + JAVA_JERSEY_SUFFIX;
        if (project.findProject(jerseyProjectName) != null) {
            String objectsProjectName = project.getName() + JAVA_OBJECTS_SUFFIX;
            if (project.findProject(objectsProjectName) == null) {
                throw new IllegalStateException(
                        String.format("Cannot enable '%s' without '%s'", jerseyProjectName, objectsProjectName));
            }

            project.project(jerseyProjectName, subproj -> {
                subproj.getPluginManager().apply(JavaPlugin.class);
                addGeneratedToMainSourceSet(subproj);
                project.getTasks().create("compileConjureJersey", ConjureGeneratorTask.class, task -> {
                    task.setDescription("Generates Jersey interfaces from your Conjure definitions "
                            + "(for use on both the client-side and server-side).");
                    task.setGroup(TASK_GROUP);
                    task.setExecutablePath(extractJavaTask::getExecutable);
                    task.setOptions(() -> optionsSupplier.get().addFlag("jersey"));
                    task.setOutputDirectory(subproj.file(JAVA_GENERATED_SOURCE_DIRNAME));
                    task.setSource(compileIrTask);

                    compileConjure.dependsOn(task);
                    subproj.getTasks().getByName("compileJava").dependsOn(task);
                    applyDependencyForIdeTasks(subproj, task);
                    task.dependsOn(
                            createWriteGitignoreTask(
                                    subproj,
                                    "gitignoreConjureJersey",
                                    subproj.getProjectDir(),
                                    JAVA_GITIGNORE_CONTENTS));
                    task.dependsOn(extractJavaTask);
                    task.dependsOn(productDependencyTask);
                });

                compileConjure.dependsOn(createJavaProductDependenciesTask(
                        project,
                        subproj,
                        "conjureJerseyProductDependency",
                        productDependencyTask));
                Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjureJersey"));

                configureJavaSubprojectDependencies(subproj, JAX_RS_API_DEP);
            });
        }
    }

    /**
     * Configures conjure-java-related default dependencies on the given project, in their own configurations, and
     * makes {@code compile} and {@code compileOnly} extend those custom configurations.
     * <p>
     * This is in order to support nebula overriding these dependencies, while also providing sensible default versions.
     *
     * @param extraDependencies  any extra dependencies we want to add to {@code compile}
     */
    private static void configureJavaSubprojectDependencies(Project subproj, String... extraDependencies) {
        ConfigurationContainer configurations = subproj.getConfigurations();
        Configuration conjureJavaLibConf = configurations.create(CONJURE_JAVA_LIB, conf -> {
            conf.defaultDependencies(deps -> {
                deps.add(subproj.getDependencies().create(CONJURE_JAVA_LIB_DEP));
                Stream.of(extraDependencies).map(subproj.getDependencies()::create).forEach(deps::add);
            });
        });
        configurations.getByName("compile", conf -> conf.extendsFrom(conjureJavaLibConf));
        Configuration conjureJavaLibCompileOnlyConf = configurations.create(
                CONJURE_JAVA_LIB_COMPILE_ONLY, conf -> {
                    conf.defaultDependencies(
                            deps -> deps.add(subproj.getDependencies().create(JAVAX_ANNOTATION_API_DEP)));
                });
        configurations.getByName("compileOnly", conf -> conf.extendsFrom(conjureJavaLibCompileOnlyConf));
    }

    private static void setupConjureTypescriptProject(
            Project project,
            Supplier<GeneratorOptions> options,
            Task compileConjure,
            Task compileIrTask,
            GenerateConjureServiceDependenciesTask productDependencyTask) {
        String typescriptProjectName = project.getName() + "-typescript";
        if (project.findProject(typescriptProjectName) != null) {
            Configuration conjureTypeScriptConfig = project.getConfigurations().maybeCreate(CONJURE_TYPESCRIPT);
            project.project(typescriptProjectName, subproj -> {
                applyDependencyForIdeTasks(subproj, compileConjure);
                File conjureTypescriptDir = new File(project.getBuildDir(), CONJURE_TYPESCRIPT);
                File srcDirectory = subproj.file("src");
                conjureTypeScriptConfig.defaultDependencies(
                        deps -> deps.add(project.getDependencies().create(CONJURE_TYPESCRIPT_BINARY)));

                ExtractExecutableTask extractConjureTypeScriptTask = ExtractExecutableTask.createExtractTask(
                        project,
                        "extractConjureTypeScript",
                        conjureTypeScriptConfig,
                        conjureTypescriptDir,
                        "conjure-typescript");
                Task compileConjureTypeScript = project.getTasks().create("compileConjureTypeScript",
                        CompileConjureTypeScriptTask.class, task -> {
                            task.setDescription(
                                    "Generates TypeScript files and a package.json from your Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.setSource(compileIrTask);
                            task.setExecutablePath(extractConjureTypeScriptTask::getExecutable);
                            task.setProductDependencyFile(productDependencyTask.getOutputFile());
                            task.setOutputDirectory(srcDirectory);
                            task.setOptions(options);
                            compileConjure.dependsOn(task);
                            task.dependsOn(
                                    createWriteGitignoreTask(
                                            subproj, "gitignoreConjureTypeScript", subproj.getProjectDir(),
                                            "/src/\n"));
                            task.dependsOn(extractConjureTypeScriptTask);
                            task.dependsOn(productDependencyTask);
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
                    task.setDescription(
                            "Runs `npm tsc` to compile generated TypeScript files into JavaScript files.");
                    task.setGroup(TASK_GROUP);
                    task.commandLine("npm", "run-script", "build");
                    task.workingDir(srcDirectory);
                    task.dependsOn(installTypeScriptDependencies);
                });
                Task publishTypeScript = project.getTasks().create("publishTypeScript", Exec.class, task -> {
                    task.setDescription("Runs `npm publish` to publish a TypeScript package "
                            + "generated from your Conjure definitions.");
                    task.setGroup(TASK_GROUP);
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
            Project project, Supplier<GeneratorOptions> options, Task compileConjure, Task compileIrTask) {
        String pythonProjectName = project.getName() + "-python";
        if (project.findProject(pythonProjectName) != null) {
            Configuration conjurePythonConfig = project.getConfigurations().maybeCreate(CONJURE_PYTHON);

            project.project(pythonProjectName, subproj -> {
                applyDependencyForIdeTasks(subproj, compileConjure);
                File conjurePythonDir = new File(project.getBuildDir(), CONJURE_PYTHON);
                File buildDir = new File(project.getBuildDir(), "python");
                File distDir = new File(buildDir, "dist");
                conjurePythonConfig.defaultDependencies(
                        deps -> deps.add(project.getDependencies().create(CONJURE_PYTHON_BINARY)));
                ExtractExecutableTask extractConjurePythonTask = ExtractExecutableTask.createExtractTask(
                        project, "extractConjurePython", conjurePythonConfig, conjurePythonDir, "conjure-python");
                Task compileConjurePython = project.getTasks().create("compileConjurePython",
                        CompileConjurePythonTask.class, task -> {
                            task.setDescription("Generates Python files from your Conjure definitions.");
                            task.setGroup(TASK_GROUP);
                            task.setSource(compileIrTask);
                            task.setExecutablePath(extractConjurePythonTask::getExecutable);
                            task.setOutputDirectory(subproj.file("python"));
                            task.setOptions(options);
                            compileConjure.dependsOn(task);
                            task.dependsOn(createWriteGitignoreTask(
                                    subproj, "gitignoreConjurePython", subproj.getProjectDir(),
                                    "/python/\n"));
                            task.dependsOn(extractConjurePythonTask);
                        });
                project.getTasks().create("buildWheel", Exec.class, task -> {
                    task.setDescription("Runs `python setup.py sdist bdist_wheel --universal` to build a python wheel "
                            + "generated from your Conjure definitions.");
                    task.setGroup(TASK_GROUP);
                    task.commandLine("python", "setup.py", "build", "--build-base", buildDir, "egg_info", "--egg-base",
                            buildDir, "sdist", "--dist-dir", distDir, "bdist_wheel", "--universal", "--dist-dir",
                            distDir);
                    task.workingDir(subproj.file("python"));
                    task.dependsOn(compileConjurePython);
                    Task cleanTask = project.getTasks().findByName(TASK_CLEAN);
                    cleanTask.dependsOn(project.getTasks().findByName("cleanCompileConjurePython"));
                });
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

    private static Task createJavaProductDependenciesTask(Project project, Project subproj,
            String taskName, GenerateConjureServiceDependenciesTask productDependencyTask) {
        return project.getTasks().create(taskName, ConjureJavaServiceDependenciesTask.class, task -> {
            task.setServiceDependencies(productDependencyTask::getConjureServiceDependencies);
            task.setSubproject(subproj);
            task.dependsOn(productDependencyTask);
            subproj.getTasks().withType(Jar.class, jar -> jar.dependsOn(task));
        });
    }

    private static Task createWriteGitignoreTask(Project project, String taskName, File outputDir, String contents) {
        WriteGitignoreTask writeGitignoreTask = project.getTasks().create(taskName, WriteGitignoreTask.class);
        writeGitignoreTask.setOutputDirectory(outputDir);
        writeGitignoreTask.setContents(contents);
        return writeGitignoreTask;
    }

    private static Task createCompileIrTask(Project project, Copy copyConjureSourcesTask) {
        Configuration conjureCompilerConfig = project.getConfigurations().maybeCreate(CONJURE_COMPILER);
        File conjureCompilerDir = new File(project.getBuildDir(), CONJURE_COMPILER);
        conjureCompilerConfig.defaultDependencies(
                deps -> deps.add(project.getDependencies().create(CONJURE_COMPILER_BINARY)));
        ExtractExecutableTask extractCompilerTask = ExtractExecutableTask.createExtractTask(
                project, "extractConjure", conjureCompilerConfig, conjureCompilerDir, "conjure");

        File irPath = Paths.get(
                project.getBuildDir().toString(), "conjure-ir", project.getName() + ".conjure.json").toFile();

        return project.getTasks().create(CONJURE_IR, CompileIrTask.class, compileIr -> {
            compileIr.setDescription("Converts your Conjure YML files into a single portable JSON file in IR format.");
            compileIr.setGroup(TASK_GROUP);
            compileIr.setInputDirectory(copyConjureSourcesTask::getDestinationDir);
            compileIr.setExecutablePath(extractCompilerTask::getExecutable);
            compileIr.setOutputFile(irPath);
            compileIr.dependsOn(copyConjureSourcesTask);
            compileIr.dependsOn(extractCompilerTask);
        });
    }

    private static Copy getConjureSources(
            Project project, org.gradle.api.internal.file.SourceDirectorySetFactory sourceDirectorySetFactory) {
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

    private static Supplier<GeneratorOptions> immutableOptionsSupplier(Supplier<GeneratorOptions> supplier) {
        return () -> new GeneratorOptions(supplier.get());
    }
}
