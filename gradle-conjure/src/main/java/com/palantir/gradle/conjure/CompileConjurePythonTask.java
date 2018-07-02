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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.InvalidUserDataException;

public class CompileConjurePythonTask extends ConjureGeneratorTask {

    @Override
    protected final Map<String, Supplier<Object>> requiredOptions() {
        return ImmutableMap.of(
                "packageName", () -> getProject().getName().replace("-", "_"),
                "packageVersion", () -> formatPythonVersion(getProject().getVersion().toString()));
    }

    private static final Pattern gradleVersion = Pattern.compile("^"
            + "(?<tag>[0-9]+\\.[0-9]+\\.[0-9]+)"
            + "(-rc(?<rc>[0-9]+))?"
            + "(-(?<distance>[0-9]+)-g(?<hash>[a-f0-9]+))?"
            + "(\\.(?<dirty>dirty))?"
            + "$");

    private static String formatPythonVersion(String stringVersion) {
        if (stringVersion.equals("unspecified")) {
            return stringVersion;
        }

        Matcher matcher = gradleVersion.matcher(stringVersion);
        if (!matcher.find()) {
            throw new InvalidUserDataException("Invalid project version " + stringVersion);
        }

        StringBuilder version = new StringBuilder();
        version.append(matcher.group("tag"));
        getGroup(matcher, "rc").ifPresent(rc -> version.append("rc").append(rc));
        getGroup(matcher, "distance").ifPresent(distance -> {
            String hash = getGroup(matcher, "hash")
                    .orElseThrow(() -> new InvalidUserDataException("Cannot specify commit distance without hash"));
            version.append("+").append(distance).append(".").append(hash);
        });
        getGroup(matcher, "dirty").ifPresent(dirty -> version.append('.').append(dirty));
        return version.toString();
    }

    private static Optional<String> getGroup(Matcher matcher, String groupName) {
        try {
            return Optional.ofNullable(matcher.group(groupName));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }
}
