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

import nebula.test.IntegrationSpec

class ConjureProductDependencyTest extends IntegrationSpec {

    def setup() {
        createFile('settings.gradle') << '''
        include 'api'
        include 'api:api-objects'
        include 'api:api-typescript'
        '''.stripIndent()

        buildFile << '''
        buildscript {
            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }
        }
        
        allprojects {
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }
            
            configurations.all {
               resolutionStrategy {
                   failOnVersionConflict()
                   force 'com.palantir.conjure.java:conjure-java:1.0.0'
                   force 'com.palantir.conjure.typescript:conjure-typescript:3.1.1'
                   force 'com.palantir.conjure.python:conjure-python:3.5.0'
                   force 'com.palantir.conjure:conjure:4.0.0'
               }
            }
        }
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

    def "no op if extension is not configured"() {
        when:
        def result = runTasksSuccessfully(":api:compileConjure")

        then:
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(":api:generateConjureProductDependency")
        result.wasSkipped(':api:conjureObjectsProductDependency')

        !fileExists("api/build/pdep.json")
    }

    def "generates pdep if extension is configured"() {
        file('api/build.gradle') << '''
        conjureDependency {
            productGroup "com.palantir.conjure"
            productName "conjure"
            minimumVersion "1.2.0"
            recommendedVersion "1.2.0"
            maximumVersion "2.x.x"
        }
        '''.stripIndent()
        when:
        runTasksSuccessfully(':api:generateConjureProductDependency')

        then:
        fileExists('api/build/pdep.json')
    }

    def "packages pdeps into java projects"() {
        file('api/build.gradle') << '''
        conjureDependency {
            productGroup "com.palantir.conjure"
            productName "conjure"
            minimumVersion "1.2.0"
            recommendedVersion "1.2.0"
            maximumVersion "2.x.x"
        }
        '''.stripIndent()

        when:
        def result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:generateConjureProductDependency')
        result.wasExecuted(':api:conjureObjectsProductDependency')

        fileExists('api/api-objects/resources/META-INF/pdep.json')
    }

    def "fails on absent fields"() {
        file('api/build.gradle') << '''
        conjureDependency {
            productName "conjure"
            minimumVersion "1.2.0"
            recommendedVersion "1.2.0"
            maximumVersion "2.x.x"
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':generateConjureProductDependency')
    }

    def "fails on invalid fields"() {
        file('api/build.gradle') << '''
        conjureDependency {
            productGroup "com.palantir.conjure"
            productName "conjure"
            minimumVersion "1.x.0"
            recommendedVersion "1.2.0"
            maximumVersion "2.x.x"
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':generateConjureProductDependency')
    }
}
