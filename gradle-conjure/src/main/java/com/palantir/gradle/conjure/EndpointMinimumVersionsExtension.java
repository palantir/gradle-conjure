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

package com.palantir.gradle.conjure;

import com.palantir.gradle.conjure.api.EndpointMinimumVersion;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.util.ConfigureUtil;

public class EndpointMinimumVersionsExtension {
    public static final String EXTENSION_NAME = "endpointVersions";
    public static final String ENDPOINT_VERSIONS_MANIFEST_KEY = "Endpoint-Minimum-Versions";

    private final SetProperty<EndpointMinimumVersion> endpointVersions;
    private final ProviderFactory providerFactory;

    @Inject
    public EndpointMinimumVersionsExtension(Project project) {
        this.endpointVersions =
                project.getObjects().setProperty(EndpointMinimumVersion.class).empty();
        this.providerFactory = project.getProviders();
    }

    public final void endpointVersion(@DelegatesTo(EndpointMinimumVersion.class) Closure<?> closure) {
        endpointVersions.add(providerFactory.provider(() -> {
            EndpointMinimumVersion emv = new EndpointMinimumVersion();
            ConfigureUtil.configureUsing(closure).execute(emv);
            return emv;
        }));
    }

    public final SetProperty<EndpointMinimumVersion> getEndpointVersions() {
        return endpointVersions;
    }
}
