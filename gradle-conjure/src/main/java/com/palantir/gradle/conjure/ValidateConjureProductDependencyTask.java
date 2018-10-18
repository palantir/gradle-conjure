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

import com.palantir.gradle.conjure.api.ConjureProductDependencyExtension;
import com.palantir.gradle.conjure.api.ProductDependency;
import com.palantir.sls.versions.SlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import java.util.Optional;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public class ValidateConjureProductDependencyTask extends DefaultTask {
    private Supplier<ConjureProductDependencyExtension> conjureProductDependency;

    @Input
    public final ConjureProductDependencyExtension getConjureProductDependency() {
        return conjureProductDependency.get();
    }

    public final Optional<ProductDependency> getValidatedProductDependency() {
        return conjureProductDependency.get().getProductDependency();
    }

    final void setConjureProductDependency(
            Supplier<ConjureProductDependencyExtension> conjureProductDependency) {
        this.conjureProductDependency = conjureProductDependency;
    }

    @TaskAction
    public final void validateProductDependency() {
        conjureProductDependency.get().getProductDependency().ifPresent(productDependency -> {
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
        });
    }
}
