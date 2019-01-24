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

class ConjurePublishTest extends IntegrationSpec {

    private static final String VERSION = '0.1.0'
    private static final String GROUP_ID = 'com.palantir.test-palantir'
    private static final String ARTIFACT_ID = 'ir-publish-test'

    def 'simple example'() {
        setup:
        // Nebula tests fails if any deprecation warnings are emitted
        System.setProperty("ignoreDeprecations", "true")
        buildFile << """
            repositories {
                mavenCentral()
                maven {
                    url 'https://dl.bintray.com/palantir/releases/'
                }
            }

            apply plugin: 'com.palantir.conjure-publish'
            group = '${GROUP_ID}'
            version = '${VERSION}'

            configurations.all {
               resolutionStrategy {
                   failOnVersionConflict()
                   force 'com.palantir.conjure:conjure:4.0.0'
               }
            }
            
            publishing {
                repositories {
                    maven {
                        name 'testRepo'
                        url "\${projectDir}/build/maven"
                    }
                }
            }
        """.stripIndent()

        settingsFile << """
            rootProject.name = '${ARTIFACT_ID}'
        """.stripIndent()

        createFile('src/main/conjure/api.yml') << '''
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

        when:
        ExecutionResult result = runTasksSuccessfully('compileIr', 'publishConjurePublicationToTestRepoRepository')

        then:
        result.success

        // check for just the distribution and no JAR files
        def groupDirectory = GROUP_ID.replaceAll('\\.', '/')
        fileExists("build/maven/${groupDirectory}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.conjure.json")
    }
}
