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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension;
import com.palantir.gradle.conjure.api.ServiceDependency;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;

public final class ConjureJavaServiceDependencies {
    public static final String SLS_RECOMMENDED_PRODUCT_DEPENDENCIES = "Sls-Recommended-Product-Dependencies";

    private ConjureJavaServiceDependencies() {}

    static void configureJavaServiceDependencies(
            Project project, ConjureProductDependenciesExtension productDependencyExt)  {

        project.afterEvaluate(p -> p.getTasks().withType(Jar.class, jar -> {
            Set<ServiceDependency> productDependencies = productDependencyExt.getProductDependencies();

            try {
                jar.getManifest().getAttributes()
                        .putIfAbsent(
                                SLS_RECOMMENDED_PRODUCT_DEPENDENCIES,
                                GenerateConjureServiceDependenciesTask.jsonMapper.writeValueAsString(
                                        new RecommendedProductDependencies(productDependencies)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private static class RecommendedProductDependencies {
        @JsonProperty("recommended-product-dependencies")
        private Set<ServiceDependency> recommendedProductDependencies;

        RecommendedProductDependencies(
                Set<ServiceDependency> recommendedProductDependencies) {
            this.recommendedProductDependencies = recommendedProductDependencies;
        }
    }
}
