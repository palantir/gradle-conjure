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

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

abstract class ExtractConjurePlugin implements Plugin<Project> {

    // configuration names
    static final String CONJURE_COMPILER = "conjureCompiler";
    static final String CONJURE_TYPESCRIPT = "conjureTypeScript";
    static final String CONJURE_PYTHON = "conjurePython";
    static final String CONJURE_JAVA = "conjureJava";

    // executable distributions
    static final String CONJURE_COMPILER_BINARY = "com.palantir.conjure:conjure";
    static final String CONJURE_JAVA_BINARY = "com.palantir.conjure.java:conjure-java";
    static final String CONJURE_TYPESCRIPT_BINARY = "com.palantir.conjure.typescript:conjure-typescript@tgz";
    static final String CONJURE_PYTHON_BINARY = "com.palantir.conjure.python:conjure-python";

    private final String taskName;
    private final String executableName;
    private final String configurationName;
    private final String dependency;

    protected ExtractConjurePlugin(
            String taskName, String executableName, String configurationName, String dependency) {
        this.taskName = taskName;
        this.executableName = executableName;
        this.configurationName = configurationName;
        this.dependency = dependency;
    }

    @Override
    public final void apply(Project project) {
        Configuration configuration = project.getConfigurations().maybeCreate(configurationName);
        File outputDir = new File(project.getBuildDir(), configurationName);
        project.getDependencies().add(configurationName, dependency);
        ExtractExecutableTask.createExtractTask(project, taskName, configuration, outputDir, executableName);
    }

    static ExtractExecutableTask applyConjureCompiler(Project project) {
        return applyAndGet(project, ExtractConjureCompilerPlugin.class, ExtractConjureCompilerPlugin.TASK_NAME);
    }

    static ExtractExecutableTask applyConjureJava(Project project) {
        return applyAndGet(project, ExtractConjureJavaPlugin.class, ExtractConjureJavaPlugin.TASK_NAME);
    }

    static ExtractExecutableTask applyConjureTypeScript(Project project) {
        return applyAndGet(project, ExtractConjureTypeScriptPlugin.class, ExtractConjureTypeScriptPlugin.TASK_NAME);
    }

    static ExtractExecutableTask applyConjurePython(Project project) {
        return applyAndGet(project, ExtractConjurePythonPlugin.class, ExtractConjurePythonPlugin.TASK_NAME);
    }

    private static ExtractExecutableTask applyAndGet(
            Project provided, Class<? extends Plugin<? extends Project>> plugin, String name) {
        Project project = provided.getRootProject();
        project.getPluginManager().apply(plugin);
        return (ExtractExecutableTask) project.getTasks().getByName(name);
    }

    static final class ExtractConjureCompilerPlugin extends ExtractConjurePlugin {
        static final String TASK_NAME = "extractConjure";

        ExtractConjureCompilerPlugin() {
            super(TASK_NAME, "conjure", CONJURE_COMPILER, CONJURE_COMPILER_BINARY);
        }
    }

    static final class ExtractConjureJavaPlugin extends ExtractConjurePlugin {
        static final String TASK_NAME = "extractConjureJava";

        ExtractConjureJavaPlugin() {
            super(TASK_NAME, "conjure-java", CONJURE_JAVA, CONJURE_JAVA_BINARY);
        }
    }

    static final class ExtractConjureTypeScriptPlugin extends ExtractConjurePlugin {
        static final String TASK_NAME = "extractConjureTypeScript";

        ExtractConjureTypeScriptPlugin() {
            super(TASK_NAME, "conjure-typescript", CONJURE_TYPESCRIPT, CONJURE_TYPESCRIPT_BINARY);
        }
    }

    static final class ExtractConjurePythonPlugin extends ExtractConjurePlugin {
        static final String TASK_NAME = "extractConjurePython";

        ExtractConjurePythonPlugin() {
            super(TASK_NAME, "conjure-python", CONJURE_PYTHON, CONJURE_PYTHON_BINARY);
        }
    }
}
