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

import com.google.common.io.Resources
import java.nio.charset.Charset
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class ConjurePublishTypeScriptTest extends IntegrationSpec {

    def setup() {
        createFile('settings.gradle') << '''
        include 'api'
        include 'api:api-typescript'
        include 'server'
        '''.stripIndent()

        createFile('build.gradle') << '''
        buildscript {
            repositories {
                mavenCentral()
                maven {
                    url 'https://dl.bintray.com/palantir/releases/'
                }
            }

            dependencies {
                classpath 'com.netflix.nebula:nebula-dependency-recommender:5.2.0'
            }
        }
        allprojects {
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
                maven {
                    url 'https://dl.bintray.com/palantir/releases/'
                }
            }
            apply plugin: 'nebula.dependency-recommender'

            dependencyRecommendations {
                strategy OverrideTransitives
                propertiesFile file: project.rootProject.file('versions.props')
            }

            configurations.all {
                resolutionStrategy {
                    failOnVersionConflict()
                }
            }
        }
        '''.stripIndent()

        createFile('api/build.gradle') << '''
        apply plugin: 'com.palantir.conjure'
        '''.stripIndent()

        createFile('versions.props') << '''
        com.google.guava:guava = 18.0
        com.palantir.conjure.typescript:conjure-typescript = 0.8.0
        com.palantir.conjure:conjure = 4.0.0-rc3
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

    def 'installs dependencies'() {
        when:
        ExecutionResult result = runTasksSuccessfully(':api:installTypeScriptDependencies')

        then:
        result.wasExecuted('api:compileConjureTypeScript')
        directory('api/api-typescript/src/node_modules').exists()
    }

    def 'installTypeScriptDependencies is up-to-date when run for the second time'() {
        when:
        !directory('api/api-typescript/src/node_modules').exists()
        ExecutionResult first = runTasksSuccessfully('installTypeScriptDependencies')

        then:
        first.wasExecuted(':api:compileConjureTypeScript') // necessary to get the package.json
        first.wasExecuted(':api:installTypeScriptDependencies')
        directory('api/api-typescript/src/node_modules').exists()

        when:
        ExecutionResult second = runTasksSuccessfully('-i', 'installTypeScriptDependencies')

        then:
        second.wasExecuted(':api:compileConjureTypeScript') // this should really be up-to-date, but something touches the output package.json which makes gradle re-run this
        second.wasUpToDate(':api:installTypeScriptDependencies')
    }

    def 'compiles TypeScript'() {
        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileTypeScript')

        then:
        result.wasExecuted('api:installTypeScriptDependencies')
        result.wasExecuted('api:compileConjureTypeScript')
        file('api/api-typescript/src/index.js').text.contains('export * from "./api";')
    }

    def 'compileConjureTypeScript is up-to-date when run for the second time'() {
        when:
        ExecutionResult first = runTasksSuccessfully('compileConjureTypeScript')
        first.wasExecuted(':api:compileConjureTypeScript')
        ExecutionResult second = runTasksSuccessfully('compileConjureTypeScript')

        then:
        second.wasUpToDate(':api:compileConjureTypeScript')
    }

    def 'publishes generated code'() {
        given:
        MockWebServer server = new MockWebServer()
        server.start(8888)
        // npm publish makes two requests to the registry
        server.enqueue(new MockResponse())
        server.enqueue(new MockResponse())
        file('api/build.gradle').text = """
        apply plugin: 'com.palantir.conjure'
        publishTypeScript.doFirst {
            file('api-typescript/src/.npmrc') << '''
            registry=http://localhost:8888
            //localhost:8888/:_password=password
            //localhost:8888/:username=test-publish
            //localhost:8888/:email=test@palantir.com
            //localhost:8888/:always-auth=true
            '''.stripIndent()
        }
        """.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully('publish')

        then:
        file('api/api-typescript/src/.npmrc').text.contains('registry=')
        result.wasExecuted('api:publishTypeScript')
        result.wasExecuted('api:compileTypeScript')

        cleanup:
        server.shutdown()
    }

    def readResource(String name) {
        return Resources.asCharSource(Resources.getResource(name), Charset.defaultCharset()).read()
    }
}
