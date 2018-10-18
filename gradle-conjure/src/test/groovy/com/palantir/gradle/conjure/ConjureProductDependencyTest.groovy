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

    def standardBuildFile = '''
        buildscript {
            repositories {
                mavenCentral()
            }
        }
        
        allprojects {
            group = 'test.abc.group'
            version = '1.0.0'
        
            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }
            configurations.all {
               resolutionStrategy {
                   failOnVersionConflict()
                   force 'com.palantir.conjure.typescript:conjure-typescript:3.1.1'
                   force 'com.palantir.conjure.python:conjure-python:3.5.0'
                   force 'com.palantir.conjure:conjure:4.0.0'
                   force 'com.palantir.conjure.postman:conjure-postman:0.1.0'
               }
           }
        }
        
        apply plugin: 'com.palantir.conjure'
    '''.stripIndent()

    def setup() {
        buildFile << standardBuildFile
    }

    def "no op if extension is not configured"() {
        expect:
        runTasksSuccessfully(":validateConjureProductDependency")
    }

    def "validation succeeds with valid extension configuration"() {
        buildFile << '''
        conjureDependency {
            productGroup "com.palantir.conjure"
            productName "conjure"
            minimumVersion "1.2.0"
            recommendedVersion "1.2.0"
            maximumVersion "2.x.x"
        }
        '''.stripIndent()

        expect:
        runTasksSuccessfully(":validateConjureProductDependency")
    }

    def "fails on absent fields"() {
        buildFile << '''
        conjureDependency {
            productName "conjure"
            minimumVersion "1.2.0"
            recommendedVersion "1.2.0"
            maximumVersion "2.x.x"
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(":validateConjureProductDependency")
    }

    def "fails on invalid fields"() {
        buildFile << '''
        conjureDependency {
            productGroup "com.palantir.conjure"
            productName "conjure"
            minimumVersion "1.x.0"
            recommendedVersion "1.2.0"
            maximumVersion "2.x.x"
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(":validateConjureProductDependency")
    }
}
