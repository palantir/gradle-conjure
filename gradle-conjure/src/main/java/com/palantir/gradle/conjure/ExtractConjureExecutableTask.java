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
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@SuppressWarnings("checkstyle:DesignForExtension") // tasks cannot be final / non public
public class ExtractConjureExecutableTask extends DefaultTask {
    /**
     * {@link ConfigurableFileCollection} is lazy.
     */
    private Configuration archive;
    private File outputDirectory;
    private String language;

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

    @OutputFile
    File getExecutable() {
        return new File(getOutputDirectory(), String.format("bin/conjure-%s", language));
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
            spec.eachFile(fcd -> fcd.setRelativePath(stripFirstName(fcd.getRelativePath())));
            spec.into(getOutputDirectory());
        });
        getLogger().info("Extracted into {}", getOutputDirectory());
        // Ensure the executable exists
        Preconditions.checkState(
                Files.exists(getExecutable().toPath()),
                "Couldn't find expected file after extracting archive %s: %s",
                tarFile,
                getExecutable());
    }

    private static RelativePath stripFirstName(RelativePath relativePath) {
        String[] segments = relativePath.getSegments();
        return new RelativePath(relativePath.isFile(), Arrays.copyOfRange(segments, 1, segments.length));
    }
}
