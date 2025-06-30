/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.conjure

import nebula.test.IntegrationTestKitSpec
import org.gradle.testkit.runner.BuildResult


abstract class ConfigurationCacheSpec extends IntegrationTestKitSpec {
    def setup() {
        definePluginOutsideOfPluginBlock = true
        keepFiles = true
    }

    Boolean fileExists(String path) {
        return new File(projectDir, path).exists()
    }

    BuildResult runTasksWithConfigurationCacheAndCheck(String... tasks) {
        def firstRun = runTasksWithConfigurationCache(false, tasks)
        assert firstRun.output.contains('Configuration cache entry stored.')
        def secondRun = runTasksWithConfigurationCache(tasks)
        assert secondRun.output.contains('Configuration cache entry reused.')

        return firstRun
    }

    BuildResult runTasksWithConfigurationCache(String... tasks) {
        return runTasksWithConfigurationCache(true, tasks)
    }

    BuildResult runTasksWithConfigurationCache(boolean cleanUp, String... tasks) {
        def run = createRunner(tasks + ['--configuration-cache'] as String[]).build()

        assert run.output.contains('Configuration cache entry stored.') ||
                run.output.contains('Configuration cache entry reused.')

        File configCacheDir = new File(projectDir, ".gradle/configuration-cache")
        if (configCacheDir.exists() && cleanUp) {
            configCacheDir.deleteDir()
        }
        if (cleanUp) {
            assert !configCacheDir.exists(), "Configuration cache directory was not deleted"
        }

        return run
    }
}
