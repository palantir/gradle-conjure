/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.conjure

import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension
import nebula.test.ProjectSpec
import org.gradle.jvm.tasks.Jar

class ConjureJavaServiceDependencyProjectSpec extends ProjectSpec {
    Jar jar

    def setup() {
        jar = project.tasks.create("jar", Jar.class)
    }

    def "generates empty product dependencies if not configured"() {
        given:
        ConjureJavaServiceDependencies.configureJavaServiceDependencies(project, new ConjureProductDependenciesExtension())

        when:
        project.evaluate()

        then:
        jar.manifest.attributes[ConjureJavaServiceDependencies.SLS_RECOMMENDED_PRODUCT_DEPENDENCIES] == '{"recommended-product-dependencies":[]}'
    }

    def "generates product dependencies if extension is configured"() {
        given:
        def ext = new ConjureProductDependenciesExtension()
        ext.serviceDependency {
            productGroup = "com.palantir.conjure"
            productName = "conjure"
            minimumVersion = "1.2.0"
            recommendedVersion = "1.2.0"
            maximumVersion = "2.x.x"
        }
        ConjureJavaServiceDependencies.configureJavaServiceDependencies(project, ext)

        when:
        project.evaluate()

        then:
        jar.manifest.attributes[ConjureJavaServiceDependencies.SLS_RECOMMENDED_PRODUCT_DEPENDENCIES] == ''+
                '{"recommended-product-dependencies":[{' +
                '"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure",' +
                '"minimum-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"recommended-version":"1.2.0"' +
                '}]}'
    }

}
