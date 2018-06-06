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

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.plugins.DefaultExtraPropertiesExtension;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

public class CompileConjureTypeScriptTask extends SourceTask {

    private static final String PACKAGE_NAME = "packageName";
    private static final String VERSION = "version";

    private File outputDirectory;
    private File executablePath;

    public final void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @OutputDirectory
    public final File getOutputDirectory() {
        return outputDirectory;
    }

    public final void setExecutablePath(File executablePath) {
        this.executablePath = executablePath;
    }

    @InputFile
    public final File getExecutablePath() {
        return executablePath;
    }


    @TaskAction
    public final void compileFiles() {
        ConfigurableFileTree fileTree = getProject().fileTree(outputDirectory);
        fileTree.exclude("node_modules/**/*");
        fileTree.forEach(File::delete);

        ConjureExtension conjureExtension = (ConjureExtension) getProject().getExtensions()
                .getByName(ConjureExtension.EXTENSION_NAME);

        if (conjureExtension == null) {
            throw new GradleException("Unable to retrieve conjure extension");
        }

        DefaultExtraPropertiesExtension typescript = conjureExtension.getTypeScriptExtension();
        List<String> additionalArgs = new ArrayList<>();
        typescript.getProperties().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(PACKAGE_NAME) && !entry.getKey().equals(VERSION))
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> {
                    additionalArgs.add("--" + entry.getKey());
                    additionalArgs.add(entry.getValue().toString());
                });

        getSource().getFiles().stream().forEach(file -> {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            builder.add("node")
                    .add(executablePath.getAbsolutePath())
                    .add("generate")
                    .add(file.getAbsolutePath())
                    .add(getValueWithDefault(typescript, PACKAGE_NAME, getProject().getName()))
                    .add(getValueWithDefault(typescript, VERSION, (String) getProject().getVersion()))
                    .add(getOutputDirectory().getAbsolutePath())
                    .addAll(additionalArgs);
            getProject().exec(execSpec -> execSpec.commandLine(builder.build()));
        });
    }

    private static String getValueWithDefault(
            DefaultExtraPropertiesExtension extension, String key, String defaultValue) {
        if (extension.has(key) && (extension.getProperty(key) instanceof String)) {
            return (String) extension.getProperty(key);
        }

        return defaultValue;
    }
}
