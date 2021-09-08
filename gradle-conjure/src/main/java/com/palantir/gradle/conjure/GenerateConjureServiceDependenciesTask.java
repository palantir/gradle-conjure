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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.palantir.gradle.conjure.api.ServiceDependency;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.sls.versions.SlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateConjureServiceDependenciesTask extends DefaultTask {
    private static final Pattern GROUP_PATTERN = Pattern.compile("^[^:@?\\s]+");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[^:@?\\s]+");
    public static final ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

    private Supplier<Set<ServiceDependency>> conjureServiceDependencies;

    public GenerateConjureServiceDependenciesTask() {
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file("service-dependencies.json"));
    }

    @Input
    public final Set<ServiceDependency> getConjureServiceDependencies() {
        return conjureServiceDependencies.get();
    }

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    final void setConjureServiceDependencies(Supplier<Set<ServiceDependency>> conjureServiceDependencies) {
        this.conjureServiceDependencies = conjureServiceDependencies;
    }

    @TaskAction
    public final void generateConjureServiceDependencies() throws IOException {
        getConjureServiceDependencies().forEach(GenerateConjureServiceDependenciesTask::validateServiceDependency);
        jsonMapper.writeValue(getOutputFile().getAsFile().get(), getConjureServiceDependencies());
    }

    private static void validateServiceDependency(ServiceDependency serviceDependency) {
        Preconditions.checkNotNull(
                serviceDependency.getProductGroup(),
                "productGroup must be specified for a recommended service dependency");
        Preconditions.checkArgument(
                GROUP_PATTERN.matcher(serviceDependency.getProductGroup()).matches(),
                "productGroup must be a valid maven group");
        Preconditions.checkNotNull(
                serviceDependency.getProductName(),
                "productName must be specified for a recommended service dependency");
        Preconditions.checkArgument(
                NAME_PATTERN.matcher(serviceDependency.getProductName()).matches(),
                "productName must be a valid maven name");
        Preconditions.checkNotNull(serviceDependency.getMinimumVersion(), "minimum version must be specified");
        Preconditions.checkNotNull(serviceDependency.getMaximumVersion(), "maximum version must be specified");
        if (!SlsVersionMatcher.safeValueOf(serviceDependency.getMaximumVersion())
                .isPresent()) {
            throw new IllegalArgumentException("maximumVersion must be valid SLS version or version matcher: "
                    + serviceDependency.getMaximumVersion());
        }
        if (!SlsVersion.check(serviceDependency.getMinimumVersion())) {
            throw new IllegalArgumentException(
                    "minimumVersion must be valid SLS version: " + serviceDependency.getMinimumVersion());
        }
        if (serviceDependency.getRecommendedVersion() != null
                && !SlsVersion.check(serviceDependency.getRecommendedVersion())) {
            throw new IllegalArgumentException(
                    "recommendedVersion must be valid SLS version: " + serviceDependency.getRecommendedVersion());
        }
        if (serviceDependency.getMinimumVersion().equals(serviceDependency.getMaximumVersion())) {
            throw new SafeIllegalArgumentException("minimumVersion and maximumVersion must be different");
        }
    }
}
