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
import java.util.function.Supplier;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

public class CompileConjureJavaTask extends SourceTask {
    private File outputDirectory;
    private File executablePath;
    private Supplier<ConjureGeneratorParameters> generatorParametersSupplier;
    private String generateTask;

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

    public final void setGeneratorParametersSupplier(Supplier<ConjureGeneratorParameters> generatorParametersSupplier) {
        this.generatorParametersSupplier = generatorParametersSupplier;
    }

    @Input
    public final ConjureGeneratorParameters getGeneratorParametersSupplier() {
        return this.generatorParametersSupplier.get();
    }

    public final void setGenerateTask(String generateTask) {
        this.generateTask = generateTask;
    }

    @TaskAction
    public final void compileFiles() {
        getSource().getFiles().stream().forEach(file -> {
            ConjureGeneratorParameters parameters = getGeneratorParametersSupplier();
            getProject().exec(execSpec -> {
                getLogging().captureStandardOutput(LogLevel.LIFECYCLE);
                getLogging().captureStandardError(LogLevel.ERROR);
                ImmutableList.Builder<String> commandArgsBuilder = ImmutableList.builder();
                commandArgsBuilder.add(
                        executablePath.getAbsolutePath(),
                        "generate",
                        file.getAbsolutePath(),
                        outputDirectory.getAbsolutePath(),
                        generateTask);

                getLogger().info("Running generator with args: {}",
                        ConjureGeneratorParametersRenderer.toArgs(parameters));
                commandArgsBuilder.addAll(ConjureGeneratorParametersRenderer.toArgs(parameters));
                execSpec.commandLine(commandArgsBuilder.build().toArray());
            });
        });
    }

}
