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

import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension;
import com.palantir.gradle.conjure.api.ServiceDependency;
import com.palantir.gradle.dist.ConfigureProductDependenciesTask;
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.RecommendedProductDependenciesPlugin;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;

final class ConjureJavaServiceDependencies {
    private ConjureJavaServiceDependencies() {}

    /*
     * We directly configure the Jar task instead of using the generated product-dependencies.json since we use gradle
     * to produce Jars which the Java generator is not aware of.
     */
    static void configureJavaServiceDependencies(
            Project project, ConjureProductDependenciesExtension productDependencyExt) {
        project.getPluginManager().apply(RecommendedProductDependenciesPlugin.class);
        project.getTasks().named("configureProductDependencies", ConfigureProductDependenciesTask.class, task -> {
            task.setProductDependencies(
                    project.provider(() -> convertDependencies(productDependencyExt.getProductDependencies())));
        });
    }

    private static Set<ProductDependency> convertDependencies(Set<ServiceDependency> serviceDependencies) {
        return serviceDependencies.stream()
                .map(serviceDependency -> new ProductDependency(
                        serviceDependency.getProductGroup(),
                        serviceDependency.getProductName(),
                        serviceDependency.getMinimumVersion(),
                        serviceDependency.getMaximumVersion(),
                        serviceDependency.getRecommendedVersion()))
                .collect(Collectors.toSet());
    }
}
