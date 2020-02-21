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
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public class CompileIrTask extends DefaultTask {
    private static final String EXECUTABLE = OsUtils.appendDotBatIfWindows("bin/conjure");

    private File outputFile;
    private Supplier<File> inputDirectory;
    private Supplier<File> executableDir;

    public final void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public final void setInputDirectory(Supplier<File> inputDirectory) {
        this.inputDirectory = inputDirectory;
    }

    @OutputFile
    public final File getOutputFile() {
        return outputFile;
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public final File getInputDirectory() {
        return inputDirectory.get();
    }

    public final void setExecutableDir(Supplier<File> executableDir) {
        this.executableDir = executableDir;
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public final File getExecutableDir() {
        return executableDir.get();
    }

    @TaskAction
    public final void generate() {
        List<String> args = ImmutableList.of(
                new File(executableDir.get(), EXECUTABLE).getAbsolutePath(),
                "compile",
                inputDirectory.get().getAbsolutePath(),
                outputFile.getAbsolutePath());

        GradleExecUtils.exec(getProject(), "generate conjure IR", args);
    }
}
