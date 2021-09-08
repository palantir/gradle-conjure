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
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

public abstract class ExtractExecutableTask extends Sync {
    private FileCollection archive;

    protected ExtractExecutableTask() {
        // Memoize this because we are re-using it in the doLast action.
        Supplier<File> tarFile = Suppliers.memoize(this::resolveTarFile);

        // Configure the spec lazily
        from((Callable<FileTree>) () -> getProject().tarTree(tarFile.get())); // will get lazily resolved
        eachFile(fcd -> fcd.setRelativePath(stripFirstName(fcd.getRelativePath())));
        into(getOutputDirectory()); // will get lazily resolved

        getExecutable().convention(getOutputDirectory().file(getExecutableName().map(s -> String.format("bin/%s", s))));

        doFirst(new Action<Task>() {
            @Override
            public void execute(Task _task) {
                Set<String> rootDirectories = new HashSet<>();
                getProject().tarTree(tarFile.get()).visit(new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                        // Note: If root dir contains only another dir (e.g. a/b), we won't get called with just that
                        // root dir 'a', but with 'a/b' directly. Hence, we look at all dirs and extract their first
                        // 'segment'.
                        String[] segments = dirDetails.getRelativePath().getSegments();
                        if (segments.length >= 1) {
                            rootDirectories.add(segments[0]);
                        }
                    }

                    @Override
                    public void visitFile(FileVisitDetails _fileDetails) {}
                });
                if (rootDirectories.size() != 1) {
                    throw new GradleException(String.format(
                            "Expected exactly one root directory in tar '%s', aborting: %s",
                            tarFile.get(), rootDirectories));
                }
            }
        });

        doLast(new Action<Task>() {
            @Override
            public void execute(Task _task) {
                getLogger().info("Extracted into {}", getOutputDirectory());
                // Ensure the executable exists
                Preconditions.checkState(
                        Files.exists(getExecutable().getAsFile().get().toPath()),
                        "Couldn't find expected file after extracting archive %s: %s",
                        tarFile.get(),
                        getExecutable());
            }
        });
    }

    public static TaskProvider<ExtractExecutableTask> createExtractTask(
            Project project, String taskName, FileCollection archive, File outputDir, String executableName) {
        return project.getTasks().register(taskName, ExtractExecutableTask.class, task -> {
            task.setArchive(archive);
            task.getOutputDirectory().set(outputDir);
            task.getExecutableName().set(executableName);
        });
    }

    @InputFiles
    public final FileCollection getArchive() {
        return archive;
    }

    final void setArchive(FileCollection archive) {
        this.archive = archive;
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * The file name of the executable. This file should exist under {@code <single root directory>/bin} inside the tar
     * archive.
     */
    @Input
    public abstract Property<String> getExecutableName();

    /** The full path to the executable that will be extracted by this task. */
    @OutputFile
    abstract RegularFileProperty getExecutable();

    private File resolveTarFile() {
        Set<File> resolvedFiles = archive.getFiles();
        Preconditions.checkState(
                resolvedFiles.size() == 1,
                "Expected exactly one dependency for executable '%s', found %s",
                getExecutableName(),
                resolvedFiles);
        return Iterables.getOnlyElement(resolvedFiles);
    }

    private static RelativePath stripFirstName(RelativePath relativePath) {
        String[] segments = relativePath.getSegments();
        return new RelativePath(relativePath.isFile(), Arrays.copyOfRange(segments, 1, segments.length));
    }
}
