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
