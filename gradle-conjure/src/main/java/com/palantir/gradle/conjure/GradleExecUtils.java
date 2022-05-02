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

import com.palantir.gradle.conjure.ConjureRunnerResource.Params;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.util.GradleVersion;

final class GradleExecUtils {

    static void exec(
            Project project, String failedTo, File executable, List<String> unloggedArgs, List<String> loggedArgs) {
        if (gradleVersionHighEnough()) {
            project.getGradle()
                    .getSharedServices()
                    .registerIfAbsent(
                            // Executable name must be the cache key, neither the spec parameters
                            // nor the class are taken into account for caching.
                            "conjure-runner-" + executable,
                            ConjureRunnerResource.class,
                            new Action<BuildServiceSpec<Params>>() {
                                @Override
                                public void execute(BuildServiceSpec<Params> spec) {
                                    spec.getParameters().getExecutable().set(executable);
                                }
                            })
                    .get()
                    .invoke(project, failedTo, unloggedArgs, loggedArgs);
        } else {
            try (ConjureRunnerResource.ConjureRunner runner = ConjureRunnerResource.createNewRunner(executable)) {
                runner.invoke(project, failedTo, unloggedArgs, loggedArgs);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // See https://github.com/gradle/gradle/issues/17434
    private static boolean gradleVersionHighEnough() {
        return GradleVersion.current().compareTo(GradleVersion.version("7.4.2")) >= 0;
    }

    private GradleExecUtils() {}
}
