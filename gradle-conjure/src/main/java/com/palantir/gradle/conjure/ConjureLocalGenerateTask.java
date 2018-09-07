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
import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class ConjureLocalGenerateTask extends ConjureGeneratorTask {

    @Override
    public final void compileFiles() {
        getSource().getFiles().stream().forEach(file -> {
            // Strip extension and version
            File outputDirectory = new File(
                    getOutputDirectory(), file.getName().substring(0, file.getName().lastIndexOf("-")));

            try {
                Files.createDirectories(outputDirectory.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Unable to create " + outputDirectory, e);
            }

            GeneratorOptions generatorOptions = getOptions();
            getProject().exec(execSpec -> {
                ImmutableList.Builder<String> commandArgsBuilder = ImmutableList.builder();
                commandArgsBuilder.add(
                        getExecutablePath().getAbsolutePath(),
                        "generate",
                        file.getAbsolutePath(),
                        outputDirectory.getAbsolutePath());

                List<String> additionalArgs = RenderGeneratorOptions.toArgs(generatorOptions, requiredOptions());
                commandArgsBuilder.addAll(additionalArgs);
                execSpec.commandLine(commandArgsBuilder.build().toArray());
            });
        });
    }
}
