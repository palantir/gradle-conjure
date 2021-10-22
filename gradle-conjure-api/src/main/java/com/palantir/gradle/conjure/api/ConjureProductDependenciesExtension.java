/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;

public class ConjureProductDependenciesExtension {

    public static final String EXTENSION_NAME = "serviceDependencies";
    public static final String ENDPOINT_VERSIONS_MANIFEST_KEY = "Endpoint-Minimum-Versions";

    private final Project project;
    private final Set<ServiceDependency> productDependencies = new HashSet<>();
    private final SetProperty<EndpointMinimumVersion> endpointVersions;
    private final ProviderFactory providerFactory;

    @Inject
    public ConjureProductDependenciesExtension(Project project) {
        this.project = project;
        this.endpointVersions =
                project.getObjects().setProperty(EndpointMinimumVersion.class).empty();
        this.providerFactory = project.getProviders();
    }

    public final Set<ServiceDependency> getProductDependencies() {
        return productDependencies;
    }

    public final void serviceDependency(@DelegatesTo(ServiceDependency.class) Closure<ServiceDependency> closure) {
        ServiceDependency serviceDependency = new ServiceDependency();
        closure.setDelegate(serviceDependency);
        closure.call();
        productDependencies.add(serviceDependency);
    }

    public final void endpointVersion(@DelegatesTo(EndpointMinimumVersion.class) Closure<?> closure) {
        endpointVersions.add(providerFactory.provider(() -> {
            EndpointMinimumVersion emv = new EndpointMinimumVersion();
            project.configure(emv, closure);
            return emv;
        }));
    }

    public final SetProperty<EndpointMinimumVersion> getEndpointVersions() {
        return endpointVersions;
    }
}
