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
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class CompileIrTask extends DefaultTask {

    private File outputFile;
    private File inputDirectory;
    private Supplier<File> executablePath;

    public final void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public final void setInputDirectory(File inputDirectory) {
        this.inputDirectory = inputDirectory;
    }

    @OutputFile
    public final File getOutputFile() {
        return outputFile;
    }

    @InputDirectory
    public final File getInputDirectory() {
        return inputDirectory;
    }

    public final void setExecutablePath(Supplier<File> executablePath) {
        this.executablePath = executablePath;
    }

    @InputFile
    public final File getExecutablePath() {
        return executablePath.get();
    }

    @TaskAction
    public final void generate() {
        getProject().exec(execSpec -> {
            ImmutableList.Builder<String> commandArgsBuilder = ImmutableList.builder();
            commandArgsBuilder.add(
                    executablePath.get().getAbsolutePath(),
                    inputDirectory.getAbsolutePath(),
                    outputFile.getAbsolutePath());

            getLogger().info("Running compiler with args: {}", commandArgsBuilder);
            execSpec.commandLine(commandArgsBuilder.build().toArray());
        });
    }
}
