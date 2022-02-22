package com.palantir.gradle.conjure;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

public interface GenerateConjureParams extends WorkParameters {
    RegularFileProperty getInputFile();

    RegularFileProperty getExecutableFile();

    RegularFileProperty getOutputDir();

    Property<String> getAction();

    ListProperty<String> getRenderedOptions();
}
