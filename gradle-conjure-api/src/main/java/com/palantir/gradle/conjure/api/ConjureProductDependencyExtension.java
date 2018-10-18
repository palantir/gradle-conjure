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

package com.palantir.gradle.conjure.api;

import java.util.Optional;

public class ConjureProductDependencyExtension {

    public static final String EXTENSION_NAME = "conjureDependency";

    private final ProductDependency productDependency = new ProductDependency();
    private boolean isConfigured = false;

    public final Optional<ProductDependency> getProductDependency() {
        if (isConfigured) {
            return Optional.of(productDependency);
        }
        return Optional.empty();
    }

    public final void setProductGroup(String productGroup) {
        isConfigured = true;
        productDependency.setProductGroup(productGroup);
    }

    public final void setProductName(String productName) {
        isConfigured = true;
        productDependency.setProductName(productName);
    }

    public final void setMinimumVersion(String minimumVersion) {
        isConfigured = true;
        productDependency.setMinimumVersion(minimumVersion);
    }

    public final void setMaximumVersion(String maximumVersion) {
        isConfigured = true;
        productDependency.setMaximumVersion(maximumVersion);
    }

    public final void setRecommendedVersion(String recommendedVersion) {
        isConfigured = true;
        productDependency.setRecommendedVersion(recommendedVersion);
    }
}
