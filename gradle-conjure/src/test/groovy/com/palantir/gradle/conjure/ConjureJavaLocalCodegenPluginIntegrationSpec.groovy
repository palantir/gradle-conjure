/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

class ConjureJavaLocalCodegenPluginIntegrationSpec extends IntegrationSpec {
    def standardBuildFile = """
        buildscript {
            repositories {
                mavenCentral()
            }
        }
        
        allprojects {
            group = 'test.group'
            version = '1.0.0'
        
            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }

            configurations.all {
               resolutionStrategy {
                   failOnVersionConflict()
                   force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure.java:conjure-lib:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'

                   force 'com.fasterxml.jackson.core:jackson-databind:2.10.2'
                   force 'com.fasterxml.jackson.core:jackson-annotations:2.10.2'
                   force 'com.palantir.safe-logging:safe-logging:1.12.0'
               }
           }
        }
        
        apply plugin: 'com.palantir.conjure-java-local'
        dependencies {
            conjure 'com.palantir.conjure:conjure-api:${TestVersions.CONJURE}'
        }
    """.stripIndent()

    def setup() {
        buildFile << standardBuildFile;
    }

    def "generates projects"() {
        buildFile << """
        conjure {
            java {
                addFlag "jersey"
                addFlag "objects"
            }
        }
        """.stripIndent()
        addSubproject("conjure-api")

        when:
        def result = runTasksSuccessfully(":conjure-api:generateConjure")

        then:
        result.wasExecuted("extractConjureIr")
        result.wasExecuted("conjure-api:generateConjure")
        fileExists("build/conjure-ir/conjure-api.conjure.json")
        fileExists('conjure-api/src/generated/java/test/group/com/palantir/conjure/spec/ConjureDefinition.java')
        result.standardOutput.contains "Running generator with args: [--jersey, --packagePrefix=test.group]"
        result.standardOutput.contains "Running generator with args: [--objects, --packagePrefix=test.group]"
    }

    def "respects user provided packagePrefix"() {
        buildFile << """
        conjure {
            java {
                addFlag "objects"
                packagePrefix = "user.group"
            }
        }
        """.stripIndent()
        addSubproject("conjure-api")

        when:
        def result = runTasksSuccessfully(":conjure-api:generateConjure")

        then:
        result.wasExecuted("extractConjureIr")
        fileExists('conjure-api/src/generated/java/user/group/com/palantir/conjure/spec/ConjureDefinition.java')
        result.standardOutput.contains "Running generator with args: [--objects, --packagePrefix=user.group]"
    }

    def 'check code compiles'() {
        addSubproject("conjure-api")
        buildFile << "conjure { java { addFlag 'objects' } }"

        when:
        ExecutionResult result = runTasksSuccessfully('check')

        then:
        result.wasExecuted(':conjure-api:compileJava')
        result.wasExecuted(':conjure-api:generateConjure')

        fileExists('conjure-api/src/generated/java/test/group/com/palantir/conjure/spec/ConjureDefinition.java')
    }

    def 'sets up idea source sets correctly'() {
        buildFile << """
        conjure { java { addFlag 'objects' } }
        subprojects {
            apply plugin: 'idea'
        }
        """.stripIndent()
        addSubproject("conjure-api")

        when:
        runTasksSuccessfully('idea')

        then:
        def slurper = new XmlParser()
        def module = slurper.parse(file('conjure-api/conjure-api.iml'))
        def sourcesFolderUrls = module.component.content.sourceFolder.@url

        sourcesFolderUrls.size() == 1
        sourcesFolderUrls.contains('file://$MODULE_DIR$/src/generated/java')
    }

    def "fails if missing corresponding subproject"() {
        when:
        buildFile << """
        task dummy {}
        """.stripIndent()
        def result = runTasksWithFailure("dummy")

        then:
        result.standardError.contains "Discovered dependencies [conjure-api] without corresponding subprojects."
    }

    def "fails if missing dependency"() {
        addSubproject("conjure-api")
        addSubproject("missing-api")
        when:
        buildFile << """
        task dummy {}
        """.stripIndent()
        def result = runTasksWithFailure("dummy")

        then:
        result.standardError.contains "Discovered subprojects [missing-api] without corresponding dependencies."
    }

    def "fails to generate without required flags"() {
        addSubproject("conjure-api")
        when:
        def result = runTasksWithFailure(":conjure-api:generateConjure")

        then:
        result.standardError.contains "Generator options must contain at least one of"
    }
}
