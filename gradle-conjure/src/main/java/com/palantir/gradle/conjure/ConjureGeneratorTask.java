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
import java.util.function.Supplier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;

public class ConjureGeneratorTask extends SourceTask {
    private File outputDirectory;
    private File executablePath;
    private Supplier<GeneratorOptions> options;

    public ConjureGeneratorTask() {
        // @TaskAction uses doFirst I think, because other actions prepended using doFirst end up happening AFTER the
        // main task
        doLast(task -> this.compileFiles());
    }

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

    public final void compileFiles() {
        getSource().getFiles().stream().forEach(file -> {
            GeneratorOptions generatorOptions = getOptions();
            getProject().exec(execSpec -> {
                ImmutableList.Builder<String> commandArgsBuilder = ImmutableList.builder();
                commandArgsBuilder.add(
                        executablePath.getAbsolutePath(),
                        "generate",
                        file.getAbsolutePath(),
                        outputDirectory.getAbsolutePath());

                List<String> additionalArgs = RenderGeneratorOptions.toArgs(generatorOptions, requiredOptions());
                getLogger().info("Running generator with args: {}", additionalArgs);
                commandArgsBuilder.addAll(additionalArgs);
                execSpec.commandLine(commandArgsBuilder.build().toArray());
            });
        });
    }

    /**
     * What options are required, along with suppliers for obtaining their default values if they were not defined in
     * the {@link #getOptions() options}.
     */
    protected Map<String, Supplier<String>> requiredOptions() {
        return ImmutableMap.of();
    }
}
