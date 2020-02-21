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

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.process.ExecResult;

final class GradleExecUtils {
    private GradleExecUtils() {}

    static void exec(Project project, String failedTo, List<String> args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExecResult execResult = project.exec(execSpec -> {
            project.getLogger().info("Running compiler with args: {}", args);
            execSpec.commandLine(args.toArray());
            execSpec.setIgnoreExitValue(true);
            execSpec.setStandardOutput(output);
            execSpec.setErrorOutput(output);
        });

        if (execResult.getExitValue() != 0) {
            throw new RuntimeException(String.format(
                    "Failed to " + failedTo + ". The command '%s' failed with exit code %d. Output:\n%s",
                    args, execResult.getExitValue(), output.toString()));
        }
    }
}
