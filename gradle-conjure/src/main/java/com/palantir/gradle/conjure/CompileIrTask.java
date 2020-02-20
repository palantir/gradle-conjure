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
import com.palantir.gradle.conjure.api.ServiceDependency;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
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
    private final SetProperty<ServiceDependency> productDependencies =
            getProject().getObjects().setProperty(ServiceDependency.class);

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

    @Input
    public final SetProperty<ServiceDependency> getProductDependencies() {
        return productDependencies;
    }

    @TaskAction
    public final void generate() {
        getProject().exec(execSpec -> {
            ImmutableList.Builder<String> commandArgsBuilder = ImmutableList.builder();
            commandArgsBuilder.add(
                    new File(executableDir.get(), EXECUTABLE).getAbsolutePath(),
                    "compile",
                    inputDirectory.get().getAbsolutePath(),
                    outputFile.getAbsolutePath(),
                    "--extensions",
                    getSerializedExtensions());

            List<String> args = commandArgsBuilder.build();
            getLogger().info("Running compiler with args: {}", args);
            execSpec.commandLine(args.toArray());
        });
    }

    private String getSerializedExtensions() {
        try {
            return GenerateConjureServiceDependenciesTask.jsonMapper.writeValueAsString(ImmutableMap.of(
                    "sls-recommended-product-dependencies",
                    getProductDependencies().get()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize conjure extensions", e);
        }
    }
}
