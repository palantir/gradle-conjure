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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public final class EndpointMinimumVersionsPlugin implements Plugin<Project> {

    public static final String CONFIGURE_ENDPOINT_MINIMUM_VERSIONS_TASK = "configureEndpointMinimumVersions";

    @Override
    public void apply(Project project) {
        EndpointMinimumVersionsExtension ext = project.getExtensions()
                .create("endpointMinimumVersions", EndpointMinimumVersionsExtension.class, project);

        project.getPluginManager().withPlugin("java", _plugin -> {
            TaskProvider<ConfigureEndpointMinimumVersionsTask> configureEndpointVersionsTask = project.getTasks()
                    .register(
                            CONFIGURE_ENDPOINT_MINIMUM_VERSIONS_TASK,
                            ConfigureEndpointMinimumVersionsTask.class,
                            cmt -> {
                                cmt.getVersions().set(ext.getEndpointVersions());
                            });

            // Ensure that the jar task depends on this wiring task
            project.getTasks()
                    .withType(Jar.class)
                    .named(JavaPlugin.JAR_TASK_NAME)
                    .configure(jar -> {
                        jar.dependsOn(configureEndpointVersionsTask);
                    });
        });
    }
}
