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
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.util.GFileUtils;

@CacheableTask
public abstract class ConjureGeneratorTask extends SourceTask {
    private Supplier<GeneratorOptions> options;

    protected ConjureGeneratorTask() {
        // @TaskAction uses doFirst I think, because other actions prepended using doFirst end up happening AFTER the
        // main task. Intentionally not using a lambda because this breaks Gradle caching
        doLast(new Action<Task>() {
            @Override
            public void execute(Task _task) {
                compileFiles();
            }
        });
    }

    // Set the path sensitivity of the sources, which would otherwise default to ABSOLUTE
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    public final FileTree getSource() {
        return super.getSource();
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExecutablePath();

    public final void setOptions(Supplier<GeneratorOptions> options) {
        this.options = options;
    }

    @Input
    public final GeneratorOptions getOptions() {
        return this.options.get();
    }

    /**
     * Where to put the output for the given input source file. This should return a directory that's under
     * {@link #getOutputDirectory()}.
     */
    protected File outputDirectoryFor(File _file) {
        return getOutputDirectory().getAsFile().get();
    }

    /** Entry point for the task. */
    public void compileFiles() {
        getSource().getFiles().forEach(file -> {
            File thisOutputDirectory = outputDirectoryFor(file);

            GFileUtils.deleteDirectory(thisOutputDirectory);
            getProject().mkdir(thisOutputDirectory);

            List<String> generateCommand =
                    ImmutableList.of("generate", file.getAbsolutePath(), thisOutputDirectory.getAbsolutePath());

            GradleExecUtils.exec(
                    getProject(),
                    "run generator",
                    OsUtils.appendDotBatIfWindows(getExecutablePath().get().getAsFile()),
                    generateCommand,
                    RenderGeneratorOptions.toArgs(getOptions(), requiredOptions(file)));
        });
    }

    /**
     * What options are required, along with suppliers for obtaining their default values if they were not defined in
     * the {@link #getOptions() options}.
     */
    protected Map<String, Supplier<Object>> requiredOptions(File _file) {
        return ImmutableMap.of();
    }
}
