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
import org.immutables.value.Value;
import org.immutables.value.Value.Parameter;

final class ConjureJavaServiceDependencies {
    public static final String SLS_RECOMMENDED_PRODUCT_DEPENDENCIES = "Sls-Recommended-Product-Dependencies";

    private ConjureJavaServiceDependencies() {}

    /*
     * We directly configure the Jar task instead of using the generated product-dependencies.json since we use gradle
     * to produce Jars which the Java generator is not aware of.
     */
    static void configureJavaServiceDependencies(
            Project project,
            ConjureProductDependenciesExtension productDependencyExt) {

        // HACKHACK Jar does not expose a lazy mechanism for configuring attributes so we have to do it after evaluation
        project.afterEvaluate(p -> p.getTasks().withType(Jar.class, jar -> {
            Set<ServiceDependency> productDependencies = productDependencyExt.getProductDependencies();

            try {
                jar.getManifest()
                        .getAttributes()
                        .putIfAbsent(
                                SLS_RECOMMENDED_PRODUCT_DEPENDENCIES,
                                GenerateConjureServiceDependenciesTask.jsonMapper.writeValueAsString(
                                        ImmutableRecommendedProductDependencies.of(productDependencies)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Value.Immutable
    interface RecommendedProductDependencies {
        @Parameter
        @JsonProperty("recommended-product-dependencies")
        Set<ServiceDependency> recommendedProductDependencies();
    }
}
