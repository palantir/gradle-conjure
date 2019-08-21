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
import nebula.test.functional.ExecutionResult

class ConjureLocalPluginTest extends IntegrationSpec {
    def standardBuildFile = """
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
                   force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure.typescript:conjure-typescript:${TestVersions.CONJURE_TYPESCRIPT}'
                   force 'com.palantir.conjure.python:conjure-python:${TestVersions.CONJURE_PYTHON}'
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                   force 'com.palantir.conjure.postman:conjure-postman:${TestVersions.CONJURE_POSTMAN}'
               }
           }
        }
        
        apply plugin: 'com.palantir.conjure-local'
        
        dependencies {
            conjure 'com.palantir.conjure:conjure-api:${TestVersions.CONJURE}'
        }
    """.stripIndent()

    def setup() {
        buildFile << standardBuildFile
    }

    def "could generate java code"() {
        addSubproject("java")
        buildFile << """
        conjure {
          java {
            dialog = true 
          }
        }
        """.stripIndent()

        when:
        // Task fails since conjure-java does not support dialog flag
        ExecutionResult result = runTasksWithFailure("generateConjure")

        then:
        result.wasExecuted(":generateJava")
        result.standardOutput.contains('Running generator with args: [--dialog')
    }

    def "fails to generate java with unsafe options"() {
        addSubproject("java")
        buildFile << """
        conjure {
          java {
            objects = true 
          }
        }
        """.stripIndent()

        when:
        ExecutionResult result = runTasksWithFailure("generateConjure")

        then:
        result.standardError.contains('Unable to generate Java bindings since unsafe options were provided')
    }

    def "generateConjure generates code in subprojects"() {
        addSubproject("typescript")
        addSubproject("python")

        when:
        ExecutionResult result = runTasksSuccessfully("generateConjure")

        then:
        result.wasExecuted(":generateTypeScript")
        result.wasExecuted(":generatePython")

        fileExists('typescript/src/conjure-api/index.ts')
        fileExists('python/python/conjure-api/conjure_spec/__init__.py')
    }

    def "custom generator throws if generator missing"() {
        addSubproject("postman")

        expect:
        ExecutionResult result1 = runTasksWithFailure("generateConjure")
        result1.standardError.contains("without corresponding generator dependency")
    }

    def 'supports custom postman generator'() {
        addSubproject("postman")

        when:
        buildFile << '''
            dependencies {
                conjureGenerators 'com.palantir.conjure.postman:conjure-postman'
            }
        '''.stripIndent()

        then:
        ExecutionResult result = runTasksSuccessfully("generateConjure")
        result.wasExecuted(":generatePostman")
        fileExists('postman/postman/conjure-api/conjure-api.postman_collection.json')
        file('postman/postman/conjure-api/conjure-api.postman_collection.json')
                .text.contains(""""version" : "${TestVersions.CONJURE}\"""")
    }
}
