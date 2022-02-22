/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.gradle.process.ExecOperations;
import org.gradle.workers.WorkAction;

public abstract class GenerateConjure implements WorkAction<GenerateConjureParams> {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    public abstract ExecOperations getExecOperations();

    @Override
    public final void execute() {
        File thisOutputDirectory = getParameters().getOutputDir().getAsFile().get();
        try {
            FileUtils.deleteDirectory(thisOutputDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        thisOutputDirectory.getAbsoluteFile().mkdir();

        File intermediateRepresentationFile =
                getParameters().getInputFile().getAsFile().get();

        List<String> generateCommand = ImmutableList.of(
                "generate", intermediateRepresentationFile.getAbsolutePath(), thisOutputDirectory.getAbsolutePath());
        GradleExecUtils.exec(
                getExecOperations(),
                getParameters().getAction().get(),
                OsUtils.appendDotBatIfWindows(
                        getParameters().getExecutableFile().getAsFile().get()),
                generateCommand,
                getParameters().getRenderedOptions().get());
    }
}
