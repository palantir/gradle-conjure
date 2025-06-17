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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

@CacheableTask
public abstract class ConjureJavaLocalGeneratorTask extends SourceTask {
    private static final ImmutableSet<String> GENERATOR_FLAGS =
            ImmutableSet.of("objects", "jersey", "undertow", "dialogue");

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract BuildServiceRegistry getBuildServiceRegistry();

    @OutputDirectory
    protected abstract DirectoryProperty getOutputDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    protected abstract RegularFileProperty getExecutablePath();

    @Input
    protected abstract MapProperty<String, Object> getOptions();

    // Set the path sensitivity of the sources, which would otherwise default to ABSOLUTE
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    public final FileTree getSource() {
        return super.getSource();
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

        File outputDir = getOutputDirectory().getAsFile().get();

        try {
            FileUtils.deleteDirectory(outputDir);
            Files.createDirectories(outputDir.toPath());
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

            @SuppressWarnings("for-rollout:PreferredInterfaceType")
            List<String> generateCommand =
                    ImmutableList.of("generate", definitionFile.getAbsolutePath(), outputDir.getAbsolutePath());

            GradleExecUtils.exec(
                    getExecOperations(),
                    getBuildServiceRegistry(),
                    "generate " + generatorFlag,
                    getExecutablePath().getAsFile().get(),
                    generateCommand,
                    RenderGeneratorOptions.toArgs(filteredOptions, Collections.emptyMap()));
        });
    }
}
