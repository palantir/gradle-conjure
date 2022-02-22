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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public abstract class ConjureJavaLocalGeneratorTask extends SourceTask {
    private static final ImmutableSet<String> GENERATOR_FLAGS =
            ImmutableSet.of("objects", "jersey", "undertow", "dialogue");

    private final RegularFileProperty executablePath = getProject().getObjects().fileProperty();
    private final DirectoryProperty outputDirectory = getProject().getObjects().directoryProperty();
    private final MapProperty<String, Object> options =
            getProject().getObjects().mapProperty(String.class, Object.class);

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract WorkerExecutor getWorkerExecutor();

    // Set the path sensitivity of the sources, which would otherwise default to ABSOLUTE
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    public final FileTree getSource() {
        return super.getSource();
    }

    @OutputDirectory
    public final DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public final RegularFileProperty getExecutablePath() {
        return executablePath;
    }

    @Input
    public final MapProperty<String, Object> getOptions() {
        return this.options;
    }

    @TaskAction
    public final void generate() {
        Preconditions.checkArgument(getSource().getFiles().size() == 1, "Exactly one input file must be specified");
        Map<String, Object> generatorOptions = getOptions().get();
        Preconditions.checkArgument(
                GENERATOR_FLAGS.stream().anyMatch(generatorOptions::containsKey),
                "Generator options must contain at least one of %s",
                GENERATOR_FLAGS);
        File definitionFile = getSource().getFiles().iterator().next();

        File outputDir = outputDirectory.getAsFile().get();

        Map<String, String> environment = System.getenv();
        WorkQueue workQueue = getWorkerExecutor().processIsolation(processWorkerSpec -> {
            processWorkerSpec.getForkOptions().setEnvironment(environment);
        });
        try {
            FileUtils.deleteDirectory(outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        GENERATOR_FLAGS.forEach(generatorFlag -> {
            if (!generatorOptions.containsKey(generatorFlag)) {
                return;
            }

            // Need to ensure we don't invoke the generator with two flags from GENERATOR_FLAGS
            Map<String, Object> filteredOptions = Maps.filterKeys(
                    generatorOptions, key -> !GENERATOR_FLAGS.contains(key) || generatorFlag.equals(key));

            workQueue.submit(GenerateConjure.class, generateConjureParams -> {
                generateConjureParams
                        .getExecutableFile()
                        .set(getExecutablePath().getAsFile().get());
                generateConjureParams.getInputFile().set(definitionFile);
                generateConjureParams.getAction().set("generate " + generatorFlag);
                generateConjureParams.getOutputDir().set(outputDir);
                generateConjureParams
                        .getRenderedOptions()
                        .set(RenderGeneratorOptions.toArgs(filteredOptions, Collections.emptyMap()));
            });
        });
    }
}
