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

class ConjurePublishTest extends IntegrationTestKitSpec {

    private static final String VERSION = '0.1.0'
    private static final String GROUP_ID = 'com.palantir.test-palantir'
    private static final String ARTIFACT_ID = 'ir-publish-test'

    def 'simple example'() {
        definePluginOutsideOfPluginBlock = true
        keepFiles = true
        setup:
        buildFile << """
            repositories {
                mavenCentral()
            }

            apply plugin: 'com.palantir.conjure-publish'
            group = '${GROUP_ID}'
            version = '${VERSION}'

            configurations.all {
               resolutionStrategy {
                   failOnVersionConflict()
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
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
        runTasksWithConfigurationCache('compileIr', 'publishConjurePublicationToTestRepoRepository')

        then:
        // check for just the distribution and no JAR files
        def groupDirectory = GROUP_ID.replaceAll('\\.', '/')
        new File(projectDir, "build/maven/${groupDirectory}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.conjure.json").exists()
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
