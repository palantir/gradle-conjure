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

import java.util.regex.Pattern;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Sync;

public abstract class ExtractConjureIrTask extends Sync {

    private static final Pattern DEFINITION_NAME =
            Pattern.compile("(.*)-([0-9]+\\.[0-9]+\\.[0-9]+(?:-rc[0-9]+)?(?:-[0-9]+-g[a-f0-9]+)?)(\\.conjure)?\\.json");

    public ExtractConjureIrTask() {
        rename(DEFINITION_NAME, "$1.conjure.json");
        from(getIrConfiguration());
        getConjureIr().set(getProject().getLayout().getBuildDirectory().dir("conjure-ir"));
        getConjureIr().disallowChanges();
        into(getConjureIr());
    }

    /** Configuration used to resolve IR. */
    @Input
    public abstract Property<Configuration> getIrConfiguration();

    @OutputDirectory
    public abstract DirectoryProperty getConjureIr();
}
