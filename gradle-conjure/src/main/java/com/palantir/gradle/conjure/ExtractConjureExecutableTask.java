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
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

class ExtractConjureExecutableTask extends DefaultTask {
    /**
     * {@link ConfigurableFileCollection} is lazy.
     */
    private Configuration archive;
    private File outputDirectory;
    private String language;
    // Output
    private final RegularFileProperty executable = getProject().getLayout().fileProperty();

    @InputFiles
    public FileCollection getArchive() {
        return archive;
    }

    void setArchive(Configuration archive) {
        this.archive = archive;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Input
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    Provider<RegularFile> getExecutableProvider() {
        return executable;
    }

    @TaskAction
    void run() {
        Set<File> resolvedFiles = archive.getFiles();
        Preconditions.checkState(resolvedFiles.size() == 1,
                "Expected exactly one %s dependency, found %s",
                archive.getName(),
                resolvedFiles);
        File tarFile = Iterables.getOnlyElement(resolvedFiles);
        getProject().copy(spec -> {
            spec.from(getProject().tarTree(tarFile));
            spec.into(getOutputDirectory());
        });
        // Find the executable
        executable.set(new File(
                outputDirectory,
                String.format(
                        "%s/bin/conjure-%s",
                        tarFile.getName().replaceAll(".tgz", ""),
                        language)));
    }
}
