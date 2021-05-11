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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.conjure.api.EndpointMinimumVersion;
import com.palantir.logsafe.Preconditions;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

public class ConfigureEndpointMinimumVersionsTask extends DefaultTask {
    private final ListProperty<EndpointMinimumVersion> endpointVersions =
            getProject().getObjects().listProperty(EndpointMinimumVersion.class);

    public ConfigureEndpointMinimumVersionsTask() {
        setDescription("Configures the 'jar' task to write the endpoint minimum versions into its manifest");
    }

    @TaskAction
    final void action() {
        if (!endpointVersions.isPresent() || endpointVersions.get().isEmpty()) {
            return;
        }
        getProject()
                .getTasks()
                .withType(Jar.class)
                .named(JavaPlugin.JAR_TASK_NAME)
                .configure(jar -> {
                    Preconditions.checkState(
                            !jar.getState().getExecuted(), "Attempted to configure jar task after it was executed");
                    jar.getManifest().from(createManifest(getProject(), endpointVersions.get()));
                });
    }

    @Input
    public final ListProperty<EndpointMinimumVersion> getVersions() {
        return endpointVersions;
    }

    private Manifest createManifest(Project project, List<EndpointMinimumVersion> versions) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        return javaConvention.manifest(manifest -> {
            String minVersionsString;
            try {
                EndpointMinimumVersions emvs = EndpointMinimumVersions.builder()
                        .minimumVersions(versions)
                        .build();
                minVersionsString = new ObjectMapper().writeValueAsString(emvs);
            } catch (JsonProcessingException e) {
                throw new GradleException("Couldn't serialize endpoint minimum versions as string", e);
            }
            manifest.attributes(ImmutableMap.of(
                    ConjureProductDependenciesExtension.ENDPOINT_VERSIONS_MANIFEST_KEY, minVersionsString));
        });
    }
}
