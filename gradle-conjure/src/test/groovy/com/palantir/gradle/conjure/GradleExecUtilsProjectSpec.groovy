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
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec

class GradleExecUtilsProjectSpec extends ProjectSpec {
    def 'running a program that exits with code 0 does not throw an exception'() {
        expect:
        ExecOperations mockExecOperations = new MockExecOperations(project)
        GradleExecUtils.exec(mockExecOperations, 'execute', new File('/bin/sh'), ['-c'], ['exit 0'])
    }

    def 'running a program that exits with a non-zero code throws an exception containing both stdout and stderr'() {
        expect:
        def baseArgs = ['-c']
        def extraArgs = ['echo foo; echo bar >&2; exit 1']

        ExecOperations mockExecOperations = new MockExecOperations(project)
        Assertions.assertThatExceptionOfType(RuntimeException).isThrownBy {
            GradleExecUtils.exec(mockExecOperations, 'fail', new File('/bin/sh'), baseArgs, extraArgs)
        }.withMessageContaining("Failed to fail.")
                .withMessageContaining((baseArgs + extraArgs).join(", "))
                .withMessageContaining("failed with exit code 1. Output:")
                .withMessageContaining("foo\n")
                .withMessageContaining("bar\n")
    }

    class MockExecOperations implements ExecOperations {
        Project project

        MockExecOperations(Project project) {
            this.project = project
        }
        @Override
        ExecResult exec(Action<? super ExecSpec> spec) {
            return project.exec(spec)
        }

        @Override
        ExecResult javaexec(Action<? super JavaExecSpec> spec) {
            return project.javaexec(spec)
        }
    }
}
