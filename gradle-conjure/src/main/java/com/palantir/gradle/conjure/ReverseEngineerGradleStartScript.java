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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Utf8;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.immutables.value.Value;

/**
 * Conjure RFC 002 mandates that a conjure generator named 'foo' should be laid out with a start script at
 * `foo-1.2.3/bin/foo`.  Given that a decent number of generators are implemented in java, the overhead of
 * starting up a whole JVM via this 'foo' start script can be significant enough to be worth optimizing.
 *
 * This class attempts to magically reverse engineer the start script and extract the classpath and main class, in
 * order to bypass this start script.
 */
final class ReverseEngineerGradleStartScript {

    /** To match stuff like {@code CLASSPATH=$APP_HOME/lib/conjure-4.13.0.jar:$APP_HOME/lib/conjure}. */
    private static final Pattern CLASSPATH_REGEX = Pattern.compile("CLASSPATH=([^\n]*)\n");

    /**
     * To match the line {@code eval set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $CONJURE_OPTS -classpath "\"$CLASSPATH\""
     * com.palantir.conjure.cli.ConjureCli "$APP_ARGS"}.
     */
    private static final Pattern MAIN_CLASS_REGEX = Pattern.compile("-classpath [^ ]+ ([a-zA-Z\\.]+)");

    static Optional<StartScriptInfo> maybeParseStartScript(Path script) {
        Optional<String> maybeString = readFileToString(script);
        if (!maybeString.isPresent()) {
            return Optional.empty();
        }
        String contents = maybeString.get();
        Path appHome = script.getParent().getParent();

        if (contents.startsWith("#!/usr/bin/env")) {
            return maybeParseUnixStartScript(appHome, contents);
        } else {
            return Optional.empty();
        }
    }

    @VisibleForTesting
    static Optional<StartScriptInfo> maybeParseUnixStartScript(Path appHome, String contents) {
        Matcher classpathMatcher = CLASSPATH_REGEX.matcher(contents);
        if (!classpathMatcher.find()) {
            return Optional.empty();
        }
        List<File> classpath = Splitter.on(':')
                .splitToStream(classpathMatcher.group(1))
                .map(s -> s.replace("$APP_HOME/", ""))
                .map(s -> appHome.resolve(s).toFile())
                .collect(Collectors.toList());

        for (File file : classpath) {
            Preconditions.checkState(file.exists(), "All files must exist", SafeArg.of("file", file));
        }

        Matcher mainClass = MAIN_CLASS_REGEX.matcher(contents);
        if (!mainClass.find()) {
            return Optional.empty();
        }

        return Optional.of(ImmutableStartScriptInfo.builder()
                .mainClass(mainClass.group(1))
                .classpath(classpath)
                .build());
    }

    /**
     * Assuming the file is a textual (bash) script, we grab the string contents. Otherwise it's probably a go/rust
     * binary, so we handle this case gracefully.
     */
    private static Optional<String> readFileToString(Path script) {
        try {
            byte[] bytes = Files.readAllBytes(script);
            if (Utf8.isWellFormed(bytes)) {
                return Optional.of(new String(bytes, StandardCharsets.UTF_8));
            } else {
                return Optional.empty();
            }
        } catch (IOException e) {
            throw new SafeRuntimeException("Unable to read file", e);
        }
    }

    @Value.Immutable
    interface StartScriptInfo {
        List<File> classpath();

        String mainClass();
    }
}
