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
                // TODO(forozco): Add IR publishing
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }
            configurations.all {
               resolutionStrategy {
                   failOnVersionConflict()
                   force 'com.palantir.conjure.typescript:conjure-typescript:3.1.1'
                   force 'com.palantir.conjure.python:conjure-python:3.5.0'
                   force 'com.palantir.conjure.java:conjure-java:2.0.0-rc2'
                   force 'com.palantir.conjure:conjure:4.0.0'
               }
           }
        }
        
        apply plugin: 'com.palantir.conjure-local'
        
        dependencies {
            conjure 'com.palantir.conjure:conjure-api:3.2.0'
        }
    '''.stripIndent()

    def standardProject = '''
        include 'typescript'
        include 'python'
        include 'java'
    '''.stripIndent()


    def setup() {
        createFile("build.gradle") << standardBuildFile
        createFile("settings.gradle") << standardProject
    }

    def "generateConjure generates code in subprojects"() {
        when:
        ExecutionResult result = runTasksSuccessfully("generateConjure")

        then:
        result.wasExecuted("generateTypeScript")
        result.wasExecuted("generatePython")
        result.wasExecuted("generateJavaObjects")
        result.wasExecuted("generateJavaJersey")

        fileExists('java/src/generated/java/com/palantir/conjure/spec/ConjureDefinition.java')
        fileExists('typescript/src/conjure-api/index.ts')
        fileExists('python/python/conjure-api/foo/__init__.py')
    }
}
