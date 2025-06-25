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
import java.util.Map;
import java.util.function.Supplier;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class CompileConjureTypeScriptTask extends ConjureGeneratorTask {
    public CompileConjureTypeScriptTask() {
        getPackageName().convention(getProject().provider(() -> getProject().getName()));
        getPackageVersion()
                .convention(
                        getProject().provider(() -> getProject().getVersion().toString()));
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getProductDependencyFile();

    @Input
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getPackageVersion();

    @TaskAction
    public final void cleanOutputDirectory() {
        File outputDir = getOutputDirectory().get().getAsFile();
        File nodeModulesDir = new File(outputDir, "node_modules");
        deleteRecursively(nodeModulesDir);
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
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
