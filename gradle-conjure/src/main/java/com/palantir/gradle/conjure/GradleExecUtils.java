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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.File;
import java.util.List;
import org.gradle.api.Project;

final class GradleExecUtils {

    static final LoadingCache<File, ConjureRunnerResource> runners = Caffeine.newBuilder()
            .build(new CacheLoader<File, ConjureRunnerResource>() {
                @Override
                public ConjureRunnerResource load(File key) {
                    return new ConjureRunnerResource(key);
                }
            });

    static void exec(
            Project project, String failedTo, File executable, List<String> unloggedArgs, List<String> loggedArgs) {
        runners.get(executable).invoke(project, failedTo, unloggedArgs, loggedArgs);
    }

    private GradleExecUtils() {}
}
