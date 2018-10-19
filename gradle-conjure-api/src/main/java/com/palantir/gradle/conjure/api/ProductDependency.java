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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Objects;

public final class ProductDependency implements Serializable {

    @JsonProperty("product-group")
    private String productGroup;

    @JsonProperty("product-name")
    private String productName;

    @JsonProperty("minimum-version")
    private String minimumVersion;

    @JsonProperty("maximum-version")
    private String maximumVersion;

    @JsonProperty("recommended-version")
    private String recommendedVersion;

    public String getProductGroup() {
        return productGroup;
    }

    public void setProductGroup(String productGroup) {
        this.productGroup = productGroup;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(String minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    public String getMaximumVersion() {
        return maximumVersion;
    }

    public void setMaximumVersion(String maximumVersion) {
        this.maximumVersion = maximumVersion;
    }

    public String getRecommendedVersion() {
        return recommendedVersion;
    }

    public void setRecommendedVersion(String recommendedVersion) {
        this.recommendedVersion = recommendedVersion;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ProductDependency that = (ProductDependency) obj;
        return Objects.equals(productGroup, that.productGroup)
                && Objects.equals(productName, that.productName)
                && Objects.equals(minimumVersion, that.minimumVersion)
                && Objects.equals(maximumVersion, that.maximumVersion)
                && Objects.equals(recommendedVersion, that.recommendedVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productGroup, productName, minimumVersion, maximumVersion, recommendedVersion);
    }
}
