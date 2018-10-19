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
import com.palantir.gradle.conjure.api.ConjureProductDependencyExtension;
import com.palantir.gradle.conjure.api.ProductDependency;
import com.palantir.sls.versions.SlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class GenerateConjureProductDependencyTask extends DefaultTask {
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);

    private Supplier<ConjureProductDependencyExtension> conjureProductDependency;

    public final Optional<ProductDependency> getConjureProductDependency() {
        return conjureProductDependency.get().getProductDependency();
    }

    @OutputFile
    public final File getOutputFile() {
        return new File(getProject().getBuildDir(), "pdep.json");
    }

    public final Optional<ProductDependency> getValidatedProductDependency() {
        return conjureProductDependency.get().getProductDependency();
    }

    final void setConjureProductDependency(
            Supplier<ConjureProductDependencyExtension> conjureProductDependency) {
        this.conjureProductDependency = conjureProductDependency;
    }

    @TaskAction
    public final void generateConjureProductDependencies() throws IOException {
        Optional<ProductDependency> maybeProductDependency = getConjureProductDependency();
        if (maybeProductDependency.isPresent()) {
            ProductDependency productDependency = maybeProductDependency.get();
            validateProductDependency(productDependency);
            jsonMapper.writeValue(getOutputFile(), productDependency);
        }
    }

    private static void validateProductDependency(ProductDependency productDependency) {
        if (productDependency.getProductGroup() == null) {
            throw new IllegalArgumentException(
                    "productGroup must be specified for a recommended product dependency");
        } else if (productDependency.getProductName() == null) {
            throw new IllegalArgumentException(
                    "productName must be specified for a recommended product dependency");
        } else if (productDependency.getMinimumVersion() == null) {
            throw new IllegalArgumentException("minimum version must be specified");
        } else if (!SlsVersion.check(productDependency.getMaximumVersion())
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
