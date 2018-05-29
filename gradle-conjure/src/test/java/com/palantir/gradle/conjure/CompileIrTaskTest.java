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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.api.file.FileTree;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CompileIrTaskTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writeToIr_writes_service_def_in_ir() throws IOException {
        ImmutableSet<File> conjureFiles = ImmutableSet.of(new File("src/test/resources/example-service.yml"));
        FileTree source = mock(FileTree.class);
        when(source.getFiles()).thenReturn(conjureFiles);
        verifyWriteToIr(source, "service.ir.json");
    }

    @Test
    public void writeToIr_combines_multiple_defs_in_ir() throws IOException {
        ImmutableSet<File> conjureFiles = ImmutableSet.of(
                new File("src/test/resources/example-errors.yml"),
                new File("src/test/resources/example-service.yml"),
                new File("src/test/resources/example-types.yml"));
        FileTree source = mock(FileTree.class);
        when(source.getFiles()).thenReturn(conjureFiles);
        verifyWriteToIr(source, "combine.ir.json");
    }

    private void verifyWriteToIr(FileTree source, String irFileName) throws IOException {
        File irFile = getIrFile(irFileName);
        CompileIrTask.writeToIr(source, irFile);
        assertThat(readFromFile(irFile))
                .isEqualTo(readFromFile(Paths.get("src/test/resources/expected/" + irFileName).toFile()));
    }

    private File getIrFile(String fileName) throws IOException {
        if (Boolean.valueOf(System.getProperty("recreate", "false"))) {
            Path output = Paths.get("src/test/resources/expected/" + fileName);
            Files.deleteIfExists(output);
            return output.toFile();
        } else {
            return Paths.get(folder.getRoot().getAbsolutePath(), fileName).toFile();
        }
    }

    private static String readFromFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

}
