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
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.Sync;

public class ExtractExecutableTask extends Sync {
    private Configuration archive;
    private File outputDirectory;
    private String executableName;

    public ExtractExecutableTask() {
        // Memoize this because we are re-using it in the doLast action.
        Supplier<File> tarFile = Suppliers.memoize(this::resolveTarFile);

        // Configure the spec lazily
        from((Callable) () -> getProject().tarTree(tarFile.get())); // will get lazily resolved
        eachFile(fcd -> fcd.setRelativePath(stripFirstName(fcd.getRelativePath())));
        into((Callable) this::getOutputDirectory); // will get lazily resolved

        doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                Set<RelativePath> rootDirectories = new HashSet<>();
                getProject().tarTree(tarFile.get()).visit(new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                        RelativePath relativePath = dirDetails.getRelativePath();
                        if (relativePath.getSegments().length == 1) {
                            rootDirectories.add(relativePath);
                        }
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails) { }
                });
                if (rootDirectories.size() != 1) {
                    throw new GradleException(String.format(
                            "Expected exactly one root directory in tar '%s', aborting: %s",
                            tarFile.get(),
                            rootDirectories));
                }
            }
        });

        doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                getLogger().info("Extracted into {}", getOutputDirectory());
                // Ensure the executable exists
                Preconditions.checkState(
                        Files.exists(getExecutable().toPath()),
                        "Couldn't find expected file after extracting archive %s: %s",
                        tarFile,
                        getExecutable());
            }
        });
    }

    @InputFiles
    public final FileCollection getArchive() {
        return archive;
    }

    final void setArchive(Configuration archive) {
        this.archive = archive;
    }

    @OutputDirectory
    public final File getOutputDirectory() {
        return outputDirectory;
    }

    final void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * The file name of the executable.
     */
    @Input
    public final String getExecutableName() {
        return executableName;
    }

    public final void setExecutableName(String executableName) {
        this.executableName = executableName;
    }

    /**
     * The full path to the executable that will be extracted by this task.
     */
    @OutputFile
    final File getExecutable() {
        return new File(getOutputDirectory(), String.format("bin/conjure-%s", executableName));
    }

    private File resolveTarFile() {
        Set<File> resolvedFiles = archive.getFiles();
        Preconditions.checkState(resolvedFiles.size() == 1,
                "Expected exactly one %s dependency, found %s",
                archive.getName(),
                resolvedFiles);
        return Iterables.getOnlyElement(resolvedFiles);
    }

    private static RelativePath stripFirstName(RelativePath relativePath) {
        String[] segments = relativePath.getSegments();
        return new RelativePath(relativePath.isFile(), Arrays.copyOfRange(segments, 1, segments.length));
    }
}
