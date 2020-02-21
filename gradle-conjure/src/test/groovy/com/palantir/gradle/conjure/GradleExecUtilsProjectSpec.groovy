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

package com.palantir.gradle.conjure

import nebula.test.ProjectSpec
import org.assertj.core.api.Assertions

class GradleExecUtilsProjectSpec extends ProjectSpec {
    def 'running a program that exits with code 0 does not throw an exception'() {
        expect:
        GradleExecUtils.exec(project, 'execute', ['sh', '-c'], ['exit 0'])
    }

    def 'running a program that exits with a non-zero code throws an exception containing both stdout and stderr'() {
        expect:
        def baseArgs = ['sh', '-c']
        def extraArgs = ['echo foo; echo bar >&2; exit 1']

        Assertions.assertThatExceptionOfType(RuntimeException).isThrownBy {
            GradleExecUtils.exec(project, 'fail', baseArgs, extraArgs)
        }.withMessageContaining("Failed to fail. The command '${baseArgs + extraArgs}' failed with exit code 1. Output:")
        .withMessageContaining("foo\n")
        .withMessageContaining("bar\n")
    }
}
