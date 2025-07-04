/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension;
import com.palantir.gradle.conjure.api.ServiceDependency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConjureProductDependenciesExtensionTest {

    private ConjureProductDependenciesExtension extension;

    @BeforeEach
    void beforeEach() {
        Project project = ProjectBuilder.builder().build();
        extension = project.getExtensions()
                .create(ConjureProductDependenciesExtension.EXTENSION_NAME, ConjureProductDependenciesExtension.class);
    }

    @Test
    void properties_areInitializedAndEmpty() {
        assertThat(extension.getProductDependencies().get()).isNotNull().isEmpty();
        assertThat(extension.getEndpointVersions().get()).isNotNull().isEmpty();
        assertThat(extension.getProductDependenciesTransformers().get())
                .isNotNull()
                .isEmpty();
    }

    @Test
    void serviceDependency_addsConfiguredDependency() {
        // Act
        extension.serviceDependency(dep -> {
            dep.setProductGroup("com.palantir.foo");
            dep.setProductName("foo-api");
            dep.setMinimumVersion("1.0.0");
        });

        // Assert
        ServiceDependency expected = new ServiceDependency();
        expected.setProductGroup("com.palantir.foo");
        expected.setProductName("foo-api");
        expected.setMinimumVersion("1.0.0");

        assertThat(extension.getProductDependencies().get()).containsExactly(expected);
    }

    @Test
    void serviceDependency_addsMultipleDependencies() {
        // Act
        extension.serviceDependency(dep -> {
            dep.setProductGroup("com.palantir.foo");
            dep.setProductName("foo-api");
            dep.setMinimumVersion("1.0.0");
        });
        extension.serviceDependency(dep -> {
            dep.setProductGroup("com.palantir.bar");
            dep.setProductName("bar-api");
            dep.setMinimumVersion("2.1.0");
        });

        // Assert
        ServiceDependency expected1 = new ServiceDependency();
        expected1.setProductGroup("com.palantir.foo");
        expected1.setProductName("foo-api");
        expected1.setMinimumVersion("1.0.0");

        ServiceDependency expected2 = new ServiceDependency();
        expected2.setProductGroup("com.palantir.bar");
        expected2.setProductName("bar-api");
        expected2.setMinimumVersion("2.1.0");

        assertThat(extension.getProductDependencies().get()).containsExactlyInAnyOrder(expected1, expected2);
    }

    @Test
    void productDependenciesTransformer_canTransformDependencies() {
        // Add an initial dependency
        ServiceDependency initialDep = new ServiceDependency();
        initialDep.setProductGroup("com.palantir.foo");
        initialDep.setProductName("foo-api");
        initialDep.setMinimumVersion("1.0.0");
        extension.getProductDependencies().add(initialDep);

        // Add a transformer that adds a new dependency
        ServiceDependency commonDep = new ServiceDependency();
        commonDep.setProductGroup("com.palantir.common");
        commonDep.setProductName("common-utils");
        commonDep.setMinimumVersion("1.0.0");

        Function<Set<ServiceDependency>, Set<ServiceDependency>> transformer = deps -> {
            Set<ServiceDependency> transformed = new HashSet<>(deps);
            transformed.add(commonDep);
            return ImmutableSet.copyOf(transformed);
        };
        extension.getProductDependenciesTransformers().add(transformer);

        // Simulate a task applying the transformers
        Set<ServiceDependency> finalDependencies =
                extension.getProductDependencies().get();
        List<Function<Set<ServiceDependency>, Set<ServiceDependency>>> transformers =
                extension.getProductDependenciesTransformers().get();

        for (Function<Set<ServiceDependency>, Set<ServiceDependency>> func : transformers) {
            finalDependencies = func.apply(finalDependencies);
        }

        // Assert
        assertThat(finalDependencies).containsExactlyInAnyOrder(initialDep, commonDep);
    }
}
