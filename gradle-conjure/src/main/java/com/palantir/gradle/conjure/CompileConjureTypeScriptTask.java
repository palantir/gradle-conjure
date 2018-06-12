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
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

public class CompileConjureTypeScriptTask extends SourceTask {

    private static final String PACKAGE_NAME = "packageName";
    private static final String VERSION = "version";

    private static final ImmutableList<String> REQUIRED_FIELDS = ImmutableList.of(PACKAGE_NAME, VERSION);

    private File outputDirectory;
    private File executablePath;
    private Supplier<ConjureGeneratorParameters> typeScriptParameters;

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

    public final void setTypeScriptParameters(Supplier<ConjureGeneratorParameters> typeScriptParameters) {
        this.typeScriptParameters = typeScriptParameters;
    }

    @Input
    public final Map<String, String> getTypeScriptProperties() {
        return this.typeScriptParameters.get().getProperties()
                .entrySet().stream()
                .filter(entry -> entry.getValue() instanceof String)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
    }

    @TaskAction
    public final void compileFiles() {
        ConfigurableFileTree fileTree = getProject().fileTree(outputDirectory);
        fileTree.exclude("node_modules/**/*");
        fileTree.forEach(File::delete);

        Map<String, String> properties = getTypeScriptProperties();
        List<String> additionalArgs = new ArrayList<>();
        properties.entrySet().stream()
                .filter(entry -> !REQUIRED_FIELDS.contains(entry.getKey()))
                .forEach(entry -> {
                    additionalArgs.add("--" + entry.getKey());
                    additionalArgs.add(entry.getValue());
                });

        getSource().getFiles().stream().forEach(file -> {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            builder.add("node")
                    .add(executablePath.getAbsolutePath())
                    .add("generate")
                    .add(file.getAbsolutePath())
                    .add(properties.getOrDefault(PACKAGE_NAME, getProject().getName()))
                    .add(properties.getOrDefault(VERSION, (String) getProject().getVersion()))
                    .add(getOutputDirectory().getAbsolutePath())
                    .addAll(additionalArgs);
            getProject().exec(execSpec -> execSpec.commandLine(builder.build()));
        });
    }
}
