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

import com.palantir.sls.versions.OrderableSlsVersion;
import java.io.File;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.tasks.CacheableTask;
import org.immutables.value.Value.Immutable;

@CacheableTask
public abstract class ConjureLocalGenerateTask extends ConjureGeneratorTask {

    protected static final Pattern PATTERN = Pattern.compile("^([^.]+)-(.+?)(\\.conjure)?\\.json$");

    static ProductNameAndVersion parseProductNameAndVersion(String filename) {
        Matcher matcher = PATTERN.matcher(filename);
        if (!matcher.matches() || matcher.groupCount() < 2) {
            throw new RuntimeException(String.format("Unable to parse conjure dependency name %s", filename));
        }
        String irName = matcher.group(1);
        Optional<OrderableSlsVersion> maybeIrVersion = OrderableSlsVersion.safeValueOf(matcher.group(2));

        if (maybeIrVersion.isEmpty()) {
            throw new RuntimeException(String.format(
                    "Unable to parse orderable SLS version from conjure dependency name %s, version %s",
                    filename, maybeIrVersion));
        }
        return ProductNameAndVersion.of(irName, maybeIrVersion.get());
    }

    @Override
    protected final File outputDirectoryFor(File file) {
        // Strip extension and version
        return getOutputDirectory()
                .dir(parseProductNameAndVersion(file.getName()).name())
                .get()
                .getAsFile();
    }

    @Immutable
    interface ProductNameAndVersion {
        String name();

        OrderableSlsVersion version();

        static ProductNameAndVersion of(String name, OrderableSlsVersion version) {
            return ImmutableProductNameAndVersion.builder()
                    .name(name)
                    .version(version)
                    .build();
        }
    }
}
