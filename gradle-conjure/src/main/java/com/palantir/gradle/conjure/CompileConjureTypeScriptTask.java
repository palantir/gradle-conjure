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

import java.io.File;
import java.util.Optional;
import java.util.function.Supplier;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

public class CompileConjureTypeScriptTask extends SourceTask {

    private File outputDirectory;
    private File executablePath;
    private Supplier<Optional<String>> packageNameSupplier;
    private Supplier<Optional<String>> versionSupplier;
    private Supplier<String> moduleTypeSupplier;

    public final void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @OutputDirectory
    public final File getOutputDirectory() {
        return outputDirectory;
    }

    public final void setExecutablePath(File executablePath) {
        this.executablePath = executablePath;
    }

    @InputFile
    public final File getExecutablePath() {
        return executablePath;
    }

    @Input
    public final String getPackageName() {
        return packageNameSupplier.get().orElse(getProject().getName());
    }

    public final void setPackageNameSupplier(Supplier<Optional<String>> packageNameSupplier) {
        this.packageNameSupplier = packageNameSupplier;
    }

    @Input
    public final String getVersion() {
        return versionSupplier.get().orElse((String) getProject().getVersion());
    }

    public final void setVersionSupplier(Supplier<Optional<String>> versionSupplier) {
        this.versionSupplier = versionSupplier;
    }

    @Input
    public final String getModuleType() {
        return moduleTypeSupplier.get();
    }

    public final void setModuleTypeSupplier(Supplier<String> moduleTypeSupplier) {
        this.moduleTypeSupplier = moduleTypeSupplier;
    }

    @TaskAction
    public final void compileFiles() {
        ConfigurableFileTree fileTree = getProject().fileTree(outputDirectory);
        fileTree.exclude("node_modules/**/*");
        fileTree.forEach(File::delete);

        getSource().getFiles().stream().forEach(file -> {
            getProject().exec(execSpec -> execSpec.commandLine("node",
                    executablePath.getAbsolutePath(),
                    "generate",
                    file.getAbsolutePath(),
                    getPackageName(),
                    getVersion(),
                    getOutputDirectory().getAbsolutePath(),
                    "--moduleType",
                    getModuleType()));
        });
    }
}
