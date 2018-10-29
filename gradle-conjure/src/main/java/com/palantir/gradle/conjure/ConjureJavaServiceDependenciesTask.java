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
import com.palantir.gradle.conjure.api.ServiceDependency;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

public class ConjureJavaServiceDependenciesTask extends DefaultTask {
    public static final String SLS_RECOMMENDED_PRODUCT_DEPENDENCIES = "Sls-Recommended-Product-Dependencies";
    private Supplier<Set<ServiceDependency>> serviceDependencies;
    private Project subproject;

    @Input
    public final Set<ServiceDependency> getServiceDependencies() {
        return serviceDependencies.get();
    }

    public final void setServiceDependencies(Supplier<Set<ServiceDependency>> serviceDependencies) {
        this.serviceDependencies = serviceDependencies;
    }

    public final void setSubproject(Project subproject) {
        this.subproject = subproject;
    }

    @TaskAction
    public final void populateServiceDependencies() throws IOException {
        for (Jar jarTask : subproject.getTasks().withType(Jar.class)) {
            jarTask.getManifest()
                    .getAttributes()
                    .put(SLS_RECOMMENDED_PRODUCT_DEPENDENCIES,
                            GenerateConjureServiceDependenciesTask.jsonMapper.writeValueAsString(
                                    new RecommendedProductDependencies(getServiceDependencies())));
        }
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
