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

import java.lang.reflect.Method;

/** Stubs used for System.exit method replacmeent. */
public final class GradleExecStubs {

    @SuppressWarnings("ThrowError")
    public static void exitStub(int status) {
        throw new ExitInvoked(status);
    }

    public static Method getStubMethod() {
        try {
            return GradleExecStubs.class.getMethod("exitStub", int.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private GradleExecStubs() {}

    // It's not generally a good idea to implement Error, however in this case we
    // want to avoid all exception handling inside
    @SuppressWarnings("ExtendsErrorOrThrowable")
    static final class ExitInvoked extends Error {
        private final int exitStatus;

        ExitInvoked(int exitStatus) {
            super("Exited with status " + exitStatus);
            this.exitStatus = exitStatus;
        }

        int getExitStatus() {
            return exitStatus;
        }
    }
}
