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
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.base.Preconditions;
import com.palantir.gradle.conjure.api.ProductDependency;
import com.palantir.sls.versions.SlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class GenerateConjureProductDependencyTask extends DefaultTask {
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);

    private Supplier<Set<ProductDependency>> conjureProductDependencies;

    @Input
    public final Set<ProductDependency> getConjureProductDependencies() {
        return conjureProductDependencies.get();
    }

    @OutputFile
    public final File getOutputFile() {
        return new File(getProject().getBuildDir(), "pdeps.json");
    }

    final void setConjureProductDependencies(
            Supplier<Set<ProductDependency>> conjureProductDependencies) {
        this.conjureProductDependencies = conjureProductDependencies;
    }

    @TaskAction
    public final void generateConjureProductDependencies() throws IOException {
        getConjureProductDependencies().forEach(GenerateConjureProductDependencyTask::validateProductDependency);
        jsonMapper.writeValue(getOutputFile(), getConjureProductDependencies());
    }

    private static void validateProductDependency(ProductDependency productDependency) {
        Preconditions.checkNotNull(productDependency.getProductGroup(),
                    "productGroup must be specified for a recommended product dependency");
        Preconditions.checkNotNull(productDependency.getProductName(),
                    "productName must be specified for a recommended product dependency");
        Preconditions.checkNotNull(productDependency.getMinimumVersion(), "minimum version must be specified");
        if (!SlsVersion.check(productDependency.getMaximumVersion())
                && !SlsVersionMatcher.safeValueOf(productDependency.getMaximumVersion()).isPresent()) {
            throw new IllegalArgumentException("maximumVersion must be valid SLS version or version matcher: "
                    + productDependency.getMaximumVersion());
        } else if (!SlsVersion.check(productDependency.getMinimumVersion())) {
            throw new IllegalArgumentException("minimumVersion must be valid SLS versions: "
                    + productDependency.getMinimumVersion());
        } else if (!SlsVersion.check(productDependency.getRecommendedVersion())) {
            throw new IllegalArgumentException("recommendedVersion must be valid SLS versions: "
                    + productDependency.getRecommendedVersion());
        } else if (productDependency.getMinimumVersion().equals(productDependency.getMaximumVersion())) {
            throw new IllegalArgumentException("minimumVersion and maximumVersion must be different");
        }
    }
}
