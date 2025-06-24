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

import nebula.test.IntegrationTestKitSpec
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class ConjureLocalPluginTest extends IntegrationTestKitSpec {
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
            }
            configurations.all {
               resolutionStrategy {
                   failOnVersionConflict()
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                   force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure.postman:conjure-postman:${TestVersions.CONJURE_POSTMAN}'
                   force 'com.palantir.conjure.python:conjure-python:${TestVersions.CONJURE_PYTHON}'
                   force 'com.palantir.conjure.typescript:conjure-typescript:${TestVersions.CONJURE_TYPESCRIPT}'
               }
           }
        }
        
        apply plugin: 'com.palantir.conjure-local'
        
        dependencies {
            conjure 'com.palantir.conjure:conjure-api:${TestVersions.CONJURE}'
        }
    """.stripIndent()

    def setup() {
        definePluginOutsideOfPluginBlock = true
        keepFiles = true
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
        BuildResult result = runTasksAndFail("generateConjure", '--info')

        then:
        result.task(":generateJava").outcome == TaskOutcome.FAILED
        result.output.contains('with args: [--dialog')
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
        BuildResult result = runTasksAndFail("generateConjure")

        then:
        result.output.contains('Unable to generate Java bindings since unsafe options were provided')
    }

    def "generateConjure generates code in subprojects"() {
        addSubproject("typescript")
        addSubproject("python")

        when:
        BuildResult result = runTasksWithConfigurationCache("generateConjure")

        then:
        result.task(":generateTypeScript").outcome == TaskOutcome.SUCCESS
        result.task(":generatePython").outcome == TaskOutcome.SUCCESS

        new File(projectDir, 'typescript/src/conjure-api/index.ts').exists()
        new File(projectDir, 'python/python/conjure-api/conjure_spec/__init__.py').exists()
    }

    def "custom generator throws if generator missing"() {
        addSubproject("postman")

        expect:
        BuildResult result1 = runTasksAndFail("generateConjure")
        result1.output.contains("without corresponding generator dependency")
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
        BuildResult result = runTasksWithConfigurationCache("generateConjure")
        result.task(":generatePostman").outcome == TaskOutcome.SUCCESS
        new File(projectDir, 'postman/postman/conjure-api/conjure-api.postman_collection.json').exists()
        file('postman/postman/conjure-api/conjure-api.postman_collection.json')
                .text.contains(""""version" : "${TestVersions.CONJURE}\"""")
    }

    private BuildResult runTasksWithConfigurationCache(String... tasks) {
        def firstRun = createRunner(tasks + ['--configuration-cache'] as String[]).build()
        assert firstRun.output.contains('Configuration cache entry stored.'),
                "Expected first run to store configuration cache, but output was: ${firstRun.output}"

        def secondRun = createRunner(tasks + ['--configuration-cache'] as String[]).build()
        assert secondRun.output.contains('Configuration cache entry reused.'),
                "Expected second run to reuse configuration cache, but output was: ${secondRun.output}"

        return firstRun
    }
}
