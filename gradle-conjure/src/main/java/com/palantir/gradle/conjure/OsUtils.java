/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import java.io.File;
import org.apache.tools.ant.taskdefs.condition.Os;

final class OsUtils {
    public static final String NPM_COMMAND_NAME = appendIfWindows(".cmd", "npm");

    private OsUtils() {}

    static String appendDotBatIfWindows(String executable) {
        return appendIfWindows(".bat", executable);
    }

    static File appendDotBatIfWindows(File executable) {
        return new File(appendDotBatIfWindows(executable.getPath()));
    }

    private static String appendIfWindows(String toAppend, String value) {
        return value + (Os.isFamily(Os.FAMILY_WINDOWS) ? toAppend : "");
    }
}
