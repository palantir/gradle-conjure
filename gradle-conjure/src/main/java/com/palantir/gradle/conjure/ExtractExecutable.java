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
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTransform
public abstract class ExtractExecutable implements TransformAction<TransformParameters.None> {

    // https://www.gnu.org/software/tar/manual/html_node/Standard.html
    private static final int TUEXEC = 00100;
    private static final int TGEXEC = 00010;
    private static final int TOEXEC = 00001;

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public final void transform(TransformOutputs outputs) {
        File tarFile = getInputArtifact().get().getAsFile();
        Preconditions.checkState(tarFile.isFile(), "Expected a file: %s", tarFile);
        File outputRoot = outputs.dir("conjure-executable");
        String outputPathForValidation = outputRoot.getAbsolutePath();
        if (!outputPathForValidation.endsWith("/")) {
            outputPathForValidation += "/";
        }
        try (FileInputStream fis = new FileInputStream(tarFile);
                InputStream bufferedFile = new BufferedInputStream(fis);
                InputStream gzipInputStream = new GZIPInputStream(bufferedFile);
                TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                if (!entry.isFile()) {
                    continue;
                }
                String name = entry.getName();
                int delimiterIndex = name.indexOf('/');
                if (delimiterIndex == 0) {
                    throw new GradleException("Absolute paths aren't supported. Found path: '" + name + "'");
                }
                String newPath = delimiterIndex == -1 ? name : name.substring(delimiterIndex + 1);
                if (newPath.isEmpty()) {
                    throw new GradleException("Invalid empty path: '" + name + "'");
                }
                File outputLocation = new File(outputRoot, newPath);
                if (!outputLocation.getAbsolutePath().startsWith(outputPathForValidation)) {
                    throw new GradleException(
                            "Tar entry cannot be extracted outside of the destination root: '" + newPath + "'");
                }
                File parentDirectory = outputLocation.getParentFile();
                if (!parentDirectory.isDirectory()) {
                    Preconditions.checkState(
                            parentDirectory.mkdirs(), "Failed to create directory '%s'", parentDirectory);
                }

                int mode = entry.getMode();
                if (!Os.isFamily(Os.FAMILY_WINDOWS) && (mode & (TUEXEC | TGEXEC | TOEXEC)) != 0) {
                    Preconditions.checkState(
                            outputLocation.createNewFile(), "Failed to create file: '%s'", outputLocation);
                    Preconditions.checkState(
                            outputLocation.setExecutable(true), "Failed to set executable: '%s'", outputLocation);
                }
                try (OutputStream outputStream = new FileOutputStream(outputLocation)) {
                    ByteStreams.copy(tarInputStream, outputStream);
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to extract the conjure executable", e);
        }
    }
}
