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

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

public final class ConjurePublishPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Ensure publishing exists before configuring IR publishing
        project.getPluginManager().apply(MavenPublishPlugin.class);
        project.getPluginManager().apply(ConjurePlugin.class);

        CompileIrTask compileIr = (CompileIrTask) project.getTasks().findByName(ConjurePlugin.CONJURE_IR);
        if (compileIr == null) {
            throw new GradleException("Unable to find compileIr task");
        }

        // Configure publishing
        project.getExtensions().configure(PublishingExtension.class, publishing -> {
            publishing.publications(publications -> {
                publications.create(
                        "conjure",
                        MavenPublication.class,
                        mavenPublication -> mavenPublication.artifact(compileIr.getOutputFile(), mavenArtifact -> {
                            mavenArtifact.builtBy(compileIr);
                            mavenArtifact.setExtension("conjure.json");
                        }));
            });
        });
    }
}
