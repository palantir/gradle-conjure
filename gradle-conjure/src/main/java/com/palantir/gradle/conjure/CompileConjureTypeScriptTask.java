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
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTask
public abstract class CompileConjureTypeScriptTask extends ConjureGeneratorTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getProductDependencyFile();

    @Input
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getPackageVersion();

    public CompileConjureTypeScriptTask() {
        doFirst(new Action<Task>() {
            @Override
            public void execute(Task _task) {
                File outputDir = getOutputDirectory().get().getAsFile();
                File[] files = outputDir.listFiles();
                if (files != null) {
                    Arrays.stream(files)
                            .filter(file -> !file.getName().equals("node_modules"))
                            .forEach(file -> {
                                try {
                                    if (file.isDirectory()) {
                                        FileUtils.deleteDirectory(file);
                                    } else {
                                        FileUtils.delete(file);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to delete: " + file, e);
                                }
                            });
                }
            }
        });
    }

    @Override
    protected final Map<String, Supplier<Object>> requiredOptions(File _file) {
        return ImmutableMap.of(
                "packageName",
                getPackageName()::get,
                "packageVersion",
                getPackageVersion()::get,
                "productDependencies",
                () -> getProductDependencyFile().getAsFile().get().getAbsolutePath());
    }
}
