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
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

public class CompileConjureTypeScriptTask extends SourceTask {

    private static final String PACKAGE_NAME = "packageName";
    private static final String VERSION = "packageVersion";

    private final Map<String, Supplier<String>> requiredFields = ImmutableMap.of(
            PACKAGE_NAME, () -> getProject().getName(),
            VERSION, () -> getProject().getVersion().toString());

    private File outputDirectory;
    private File executablePath;
    private Supplier<GeneratorOptions> options;

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

    public final void setOptions(Supplier<GeneratorOptions> options) {
        this.options = options;
    }

    @Input
    public final GeneratorOptions getOptions() {
        return this.options.get();
    }

    @TaskAction
    public final void compileFiles() {
        ConfigurableFileTree fileTree = getProject().fileTree(outputDirectory);
        fileTree.exclude("node_modules/**/*");
        fileTree.forEach(File::delete);

        GeneratorOptions generatorOptions = new GeneratorOptions(getOptions());
        requiredFields.forEach((field, defaultSupplier) -> {
            String defaultValue = defaultSupplier.get();
            if (!generatorOptions.has(field)) {
                getLogger().info("Field '{}' was not defined in options, falling back to default: {}",
                        field,
                        defaultValue);
                generatorOptions.setProperty(field, defaultValue);
            } else if (Objects.equals(defaultValue, Objects.toString(generatorOptions.get(field)))) {
                getLogger().warn("Field '{}' was defined in options but its value is the same as the default: {}",
                        field,
                        defaultValue);
            }
        });
        List<String> additionalArgs = RenderGeneratorOptions.toArgs(generatorOptions);

        getSource().getFiles().forEach(file -> {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            builder.add(executablePath.getAbsolutePath())
                    .add("generate")
                    .add(file.getAbsolutePath())
                    .add(getOutputDirectory().getAbsolutePath())
                    .addAll(additionalArgs);

            getLogger().info("Running generator with args: {}", additionalArgs);
            getProject().exec(execSpec -> execSpec.commandLine(builder.build()));
        });
    }
}
