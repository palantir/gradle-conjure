/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import org.junit.Test;

public class ReverseEngineerJavaStartScriptTest {

    @Test
    public void dont_freak_out_with_windows_files() {
        assertThat(ReverseEngineerJavaStartScript.maybeParseStartScript(
                        Paths.get("/Volumes/git/log-receiver/log-receiver-api/build/conjureCompiler/bin/conjure.bat")))
                .isEmpty();
    }

    @Test
    public void real_thingy() {
        assertThat(ReverseEngineerJavaStartScript.maybeParseStartScript(
                        Paths.get("/Volumes/git/log-receiver/log-receiver-api/build/conjureCompiler/bin/conjure")))
                .hasValueSatisfying(info -> {
                    assertThat(info.mainClass()).isEqualTo("com.palantir.conjure.cli.ConjureCli");
                });
    }
}
