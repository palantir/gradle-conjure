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
import com.palantir.gradle.conjure.api.ServiceDependency;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class CompileIrTask extends DefaultTask {
    private static final String EXECUTABLE = OsUtils.appendDotBatIfWindows("bin/conjure");

    public CompileIrTask() {
        getConjureExtensions().convention(new HashMap<>());
    }

    /**
     * Eagerly set where to output the generated IR.
     *
     * @deprecated Use {@link #getOutputIrFile()} instead.
     */
    @Deprecated
    public final void setOutputFile(File outputFile) {
        getOutputIrFile().set(outputFile);
    }

    /**
     * Eagerly get where to output the generated IR.
     *
     * @deprecated Use {@link #getOutputIrFile()} instead.
     */
    @Deprecated
    @Internal
    public final File getOutputFile() {
        return getOutputIrFile().getAsFile().get();
    }

    @OutputFile
    public abstract RegularFileProperty getOutputIrFile();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getInputDirectory();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getExecutableDir();

    @Input
    public abstract SetProperty<ServiceDependency> getProductDependencies();

    @Input
    @Optional
    public abstract MapProperty<String, Serializable> getConjureExtensions();

    @Input
    @Optional
    public abstract MapProperty<String, Object> getOptions();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getExtensionsFile();

    @TaskAction
    public final void generate() {
        File executable = new File(getExecutableDir().getAsFile().get(), EXECUTABLE);
        List<String> args = ImmutableList.<String>builder()
                .add("compile")
                .add(getInputDirectory().get().getAsFile().getAbsolutePath())
                .add(getOutputIrFile().get().getAsFile().getAbsolutePath())
                .add("--extensions")
                .add(OsUtils.escapeAndWrapArgIfWindows(getSerializedExtensions()))
                .addAll(RenderGeneratorOptions.toArgs(getOptions().get(), Collections.emptyMap()))
                .build();

        GradleExecUtils.exec(getProject(), "generate conjure IR", executable, Collections.emptyList(), args);
    }

    private String getSerializedExtensions() {
        try {
            Map<Object, Object> extData = new HashMap<>();
            if (getExtensionsFile().isPresent()) {
                extData = GenerateConjureServiceDependenciesTask.jsonMapper.readValue(
                        getExtensionsFile().getAsFile().get(), Map.class);
            }
            extData.putAll(getConjureExtensions().get());
            extData.put(
                    "recommended-product-dependencies", getProductDependencies().get());
            return GenerateConjureServiceDependenciesTask.jsonMapper.writeValueAsString(extData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize conjure extensions", e);
        }
    }
}
