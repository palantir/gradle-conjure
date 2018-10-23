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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.gradle.conjure.api.ProductDependency;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

public class ConjureJavaProductDependenciesTask extends DefaultTask {
    public static final String SLS_RECCOMENDED_PRODUCT_DEPS_KEY = "Sls-Recommended-Product-Dependencies";
    private Supplier<Set<ProductDependency>> productDependencies;
    private Project subproject;

    @Input
    public final Set<ProductDependency> getProductDependencies() {
        return productDependencies.get();
    }

    public final void setProductDependencies(Supplier<Set<ProductDependency>> productDependencies) {
        this.productDependencies = productDependencies;
    }

    public final void setSubproject(Project subproject) {
        this.subproject = subproject;
    }

    @TaskAction
    public final void populateProductDependencies() throws IOException {
        for (Jar jarTask : subproject.getTasks().withType(Jar.class)) {
            jarTask.getManifest()
                    .getAttributes()
                    .put(SLS_RECCOMENDED_PRODUCT_DEPS_KEY,
                            GenerateConjureProductDependenciesTask.jsonMapper.writeValueAsString(
                                    new RecommendedProductDependencies(getProductDependencies())));
        }
    }

    private static class RecommendedProductDependencies {
        @JsonProperty("recommended-product-dependencies")
        private Set<ProductDependency> recommendedProductDependencies;

        RecommendedProductDependencies(
                Set<ProductDependency> recommendedProductDependencies) {
            this.recommendedProductDependencies = recommendedProductDependencies;
        }
    }
}
