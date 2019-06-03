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

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class ConjureLocalJavaPluginTest extends IntegrationSpec {
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
                   force 'com.palantir.conjure:conjure:4.0.0'
                   force 'com.palantir.conjure.java:conjure-java:3.8.1'
               }
           }
        }
        
        apply plugin: 'com.palantir.conjure-local-java'
        
        dependencies {
            conjure 'com.palantir.conjure:conjure-api:4.1.1'
            conjureGenerators 'com.palantir.conjure.java:conjure-java'
        }
        
        conjure {
            options 'java', {
                addFlag('objects')
            }
        }
    '''.stripIndent()

    def setup() {
        buildFile << standardBuildFile
        addSubproject("java")
    }

    def "generates Java projects with conjure-java"() {
        when:
        ExecutionResult result = runTasksSuccessfully("generateConjure")

        then:
        result.wasExecuted(":generateConjureJavaJava")
        fileExists('java/src/generated/java/')
    }

    def "generates suitable idea module"() {
        when:
        buildFile << """
            allprojects {
                apply plugin: 'idea'
            }
        """.stripIndent()
        ExecutionResult result = runTasksSuccessfully("idea")

        then:
        result.wasExecuted(":generateConjureJavaJava")
        fileExists('java/java.iml')
        file('java/java.iml').text.contains('src/generated/java')
    }

    def "custom generator throws if generator missing"() {
        addSubproject("foo")

        expect:
        ExecutionResult result = runTasksWithFailure("generateConjure")
        result.standardOutput.contains("without corresponding generator dependency")
    }
}
