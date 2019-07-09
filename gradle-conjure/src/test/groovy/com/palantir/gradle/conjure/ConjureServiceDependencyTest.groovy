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

package com.palantir.gradle.conjure

import java.util.jar.Manifest
import java.util.zip.ZipFile
import nebula.test.IntegrationSpec

class ConjureServiceDependencyTest extends IntegrationSpec {

    def setup() {
        addSubproject('api')
        addSubproject('api:api-objects')
        addSubproject('api:api-jersey')
        addSubproject('api:api-typescript')

        buildFile << '''
        buildscript {
            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }
            dependencies {
                classpath 'com.netflix.nebula:nebula-dependency-recommender:5.2.0'
            }
        }
        
        allprojects {
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }
            
            apply plugin: 'nebula.dependency-recommender'

            dependencyRecommendations {
                strategy OverrideTransitives
                propertiesFile file: project.rootProject.file('versions.props')
            }
        }
        '''.stripIndent()

        createFile('versions.props') << '''
        com.fasterxml.jackson.*:* = 2.6.7
        com.google.guava:guava = 18.0
        com.palantir.conjure.typescript:conjure-typescript = 3.8.0
        com.palantir.conjure.java:* = 2.3.0
        com.palantir.conjure:conjure = 4.0.0
        com.squareup.retrofit2:retrofit = 2.1.0
        javax.annotation:javax.annotation-api = 1.3.2
        javax.ws.rs:javax.ws.rs-api = 2.0.1
        '''.stripIndent()

        createFile('api/build.gradle') << '''
        apply plugin: 'com.palantir.conjure'
        '''.stripIndent()

        createFile('api/src/main/conjure/api.yml') << '''
        types:
          definitions:
            default-package: test.test.api
            objects:
              StringExample:
                fields:
                  string: string
        services:
          TestServiceFoo:
            name: Test Service Foo
            package: test.test.api

            endpoints:
              post:
                http: POST /post
                args:
                  object: StringExample
                returns: StringExample
        '''.stripIndent()
        file("gradle.properties") << "org.gradle.daemon=false"
    }

    def "generates empty product dependencies if not configured"() {
        when:
        runTasksSuccessfully(':api:generateConjureServiceDependencies')

        then:
        fileExists("api/build/service-dependencies.json")
        file('api/build/service-dependencies.json').text == '[]'
    }

    def "generates product dependencies if extension is configured"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        runTasksSuccessfully(':api:generateConjureServiceDependencies')

        then:
        fileExists('api/build/service-dependencies.json')
        file('api/build/service-dependencies.json').text.contains('"product-group":"com.palantir.conjure"')
        file('api/build/service-dependencies.json').text.contains('"product-name":"conjure"')
        file('api/build/service-dependencies.json').text.contains('"minimum-version":"1.2.0"')
        file('api/build/service-dependencies.json').text.contains('"maximum-version":"2.x.x"')
        file('api/build/service-dependencies.json').text.contains('"recommended-version":"1.2.0"')
    }

    def "correctly passes product dependencies to generators"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:generateConjureServiceDependencies')
        file('api/api-typescript/src/package.json').text.contains('sls')
    }

    def "does not pass product dependencies to java objects"() {
         file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-objects:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        readRecommendedProductDeps(file('api/api-objects/build/libs/api-objects-0.1.0.jar')) == null
    }

    def "correctly configures manifest for java jersey"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        def recommendedDeps = readRecommendedProductDeps(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        recommendedDeps == '{"recommended-product-dependencies":[{' +
                '"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure",' +
                '"minimum-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"recommended-version":"1.2.0"}]}'
    }

    def "fails on absent fields"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "fails on invalid version"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.x.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "fails on invalid group"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir:conjure"
                productName = "conjure"
                minimumVersion = "1.0.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def readRecommendedProductDeps(File jarFile) {
        def zf = new ZipFile(jarFile)
        def manifestEntry = zf.getEntry("META-INF/MANIFEST.MF")
        def manifest = new Manifest(zf.getInputStream(manifestEntry))
        return manifest.getMainAttributes().getValue(
                ConjureJavaServiceDependencies.SLS_RECOMMENDED_PRODUCT_DEPENDENCIES)
    }
}
