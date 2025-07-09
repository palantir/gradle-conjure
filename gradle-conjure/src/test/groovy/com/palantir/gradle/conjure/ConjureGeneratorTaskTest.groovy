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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class ConjureGeneratorTaskTest extends ConfigurationCacheSpec {
    def setup() {
        createFile('settings.gradle') << """
        include 'api'
        include 'api:api-objects'
        """.stripIndent()

        createFile('build.gradle') << """
        allprojects {
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
            }
            configurations {
                conjureCompiler
                conjureJava
            }
            dependencies {
                conjureCompiler 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                conjureJava 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
            }
        }
        """.stripIndent()

        createFile('api/build.gradle') << """
        apply plugin: 'com.palantir.conjure'
        """.stripIndent()

        createFile('api/src/main/conjure/api.yml') << '''
        types:
          definitions:
            default-package: test.test.api
            objects:
              StringExample:
                fields:
                  string: string
        '''.stripIndent()
        file("gradle.properties") << "org.gradle.daemon=false"
    }

    def "generates all files"() {
        when:
        BuildResult result = runTasksWithConfigurationCache(':api:compileConjure')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(':api:compileConjure', ':api:compileConjureObjects', ':api:compileIr')

        // java
        new File(projectDir, 'api/api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java')
    }

    def "cleans up old files"() {
        when:
        BuildResult result = runTasksWithConfigurationCache(':api:compileConjure')
        file('api/src/main/conjure/api.yml').text = '''
        types:
          definitions:
            default-package: test.test.api
            objects:
              NewStringExample:
                fields:
                  string: string
        '''.stripIndent()
        BuildResult result2 = runTasksWithConfigurationCache(':api:compileConjure')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(':api:compileConjure', ':api:compileConjureObjects', ':api:compileIr')

        result2.tasks(TaskOutcome.SUCCESS)*.path.containsAll(':api:compileConjure', ':api:compileConjureObjects', ':api:compileIr')

        // java
        !fileExists( 'api/api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java')
        fileExists( 'api/api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/NewStringExample.java')
    }

    def 'when a file has errors the error is reported in the exception'() {
        when:
        file('api/src/main/conjure/bad.yml').text = '''
            this-is-invalid
        '''.stripIndent()

        def output = runTasksAndFail(':api:compileIr').output

        then:
        output.contains('Cannot construct instance of')
    }
}
