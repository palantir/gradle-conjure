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

import com.palantir.conjure.defs.Conjure;
import com.palantir.conjure.spec.ConjureDefinition;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

public class CompileIrTask extends SourceTask {

    private File outputFile;

    public final void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @OutputFile
    public final File getOutputFile() {
        return outputFile;
    }

    @TaskAction
    public final void generate() throws IOException {
        writeToIr(getSource(), outputFile);
    }

    protected static void writeToIr(FileTree source, File destination) throws IOException {
        Set<File> ymlFiles = source.getFiles();
        ConjureDefinition definition = Conjure.parse(ymlFiles);
        ConjureDefinitions.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(destination, definition);
    }
}
