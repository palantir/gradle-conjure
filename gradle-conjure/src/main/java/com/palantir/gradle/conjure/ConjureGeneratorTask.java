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

import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
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
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public abstract class ConjureGeneratorTask extends SourceTask {
    private Supplier<GeneratorOptions> options;

    public ConjureGeneratorTask() {
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

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract WorkerExecutor getWorkerExecutor();

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
        Map<String, String> environment = System.getenv();
        WorkQueue workQueue = getWorkerExecutor().processIsolation(processWorkerSpec -> {
            processWorkerSpec.getForkOptions().setEnvironment(environment);
        });
        for (File sourceFile : getSource().getFiles()) {
            File thisOutputDirectory = outputDirectoryFor(sourceFile);
            try {
                FileUtils.deleteDirectory(thisOutputDirectory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            workQueue.submit(GenerateConjure.class, generateConjureParams -> {
                generateConjureParams.getInputFile().set(sourceFile);
                generateConjureParams.getOutputDir().set(thisOutputDirectory);
                generateConjureParams
                        .getExecutableFile()
                        .set(getExecutablePath().get().getAsFile());
                generateConjureParams.getAction().set("run generator");
                generateConjureParams
                        .getRenderedOptions()
                        .set(RenderGeneratorOptions.toArgs(getOptions(), requiredOptions(sourceFile)));
            });
        }
    }

    /**
     * What options are required, along with suppliers for obtaining their default values if they were not defined in
     * the {@link #getOptions() options}.
     */
    protected Map<String, Supplier<Object>> requiredOptions(File _file) {
        return ImmutableMap.of();
    }
}
