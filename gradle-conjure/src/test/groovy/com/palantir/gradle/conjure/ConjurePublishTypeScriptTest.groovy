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

import java.util.concurrent.TimeUnit

class ConjurePublishTypeScriptTest extends IntegrationSpec {

    def setup() {
        createFile('settings.gradle') << '''
        include 'api'
        include 'api:api-typescript'
        include 'server'
        '''.stripIndent()

        createFile('build.gradle') << """
        buildscript {
            repositories {
                mavenCentral()
            }
        }
        
        allprojects {
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
            }
            configurations.all {
               resolutionStrategy {
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                   force 'com.palantir.conjure.typescript:conjure-typescript:${TestVersions.CONJURE_TYPESCRIPT}'
               }
           }
        }
        """.stripIndent()

        createFile('api/build.gradle') << '''
        apply plugin: 'com.palantir.conjure'
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

    def 'generateNpmrc uses custom registry'() {
        given:
        MockWebServer server = new MockWebServer()
        server.start(8888)
        // generateNpmrc will make request for token
        server.enqueue(new MockResponse().setBody("{\"token\": \"42\"}"))
        // npm install makes two requests to the registry
        server.enqueue(new MockResponse().setBody("""
        {
          "name": "conjure-client",
          "version": "0.0.0",
          "author": "Palantir Technologies, Inc",
          "license": "Apache-2.0"
        }
        """.stripIndent()))
        server.enqueue(new MockResponse().setBody("""
        {
          "name": "typescript",
          "version": "0.0.0",
          "author": "Palantir Technologies, Inc",
          "license": "Apache-2.0"
        }
        """.stripIndent()))

        file('api/build.gradle').text = """
        apply plugin: 'com.palantir.conjure'

        generateNpmrc.registryUri = "http://localhost:8888"
        generateNpmrc.username = "user"
        generateNpmrc.password = "pass"
        """.stripIndent()

        when:
        ExecutionResult result = runTasks('installTypeScriptDependencies')

        then:
        server.requestCount == 3
        server.takeRequest(100, TimeUnit.MILLISECONDS).path == "/-/user/org.couchdb.user:user"
        server.takeRequest(100, TimeUnit.MILLISECONDS).path == "/conjure-client"
        server.takeRequest(100, TimeUnit.MILLISECONDS).path == "/typescript"
        file('api/api-typescript/src/.npmrc').exists()
        file('api/api-typescript/src/.npmrc').readLines()
                .containsAll('registry=http://localhost:8888/', '//localhost:8888/:_authToken=42')
        result.wasExecuted('api:generateNpmrc')
        !result.wasSkipped('api:generateNpmrc')
        !result.wasUpToDate('api:generateNpmrc')
        result.wasExecuted('api:compileConjureTypeScript')
        result.wasExecuted('api:installTypeScriptDependencies')

        cleanup:
        server.shutdown()
    }

    def 'compiles TypeScript'() {
        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileTypeScript')

        then:
        result.wasExecuted('api:installTypeScriptDependencies')
        result.wasExecuted('api:compileConjureTypeScript')
        file('api/api-typescript/src/index.js').text.contains('export * from "./api";')
    }

    def 'compileTypeScript is up-to-date when run for the second time'() {
        when:
        ExecutionResult first = runTasksSuccessfully('compileTypeScript')
        first.wasExecuted(':api:compileTypeScript')
        ExecutionResult second = runTasksSuccessfully('compileTypeScript')

        then:
        second.wasUpToDate(':api:compileTypeScript')
    }

    def 'publishes generated code'() {
        given:
        MockWebServer server = new MockWebServer()
        server.start(8888)
        // generateNpmrc will make request for token
        server.enqueue(new MockResponse().setBody("{\"token\": \"atoken\"}"))
        // npm publish makes two requests to the registry
        server.enqueue(new MockResponse())
        server.enqueue(new MockResponse())
        file('api/build.gradle').text = """
        apply plugin: 'com.palantir.conjure'

        conjure {
            typescript {
                installGeneratesNpmrc = false // test relies on pulling from actual https://registry.npmjs.org
            }
        }

        generateNpmrc.registryUri = "http://localhost:8888"
        generateNpmrc.username = "user"
        generateNpmrc.password = "pass"
        """.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully('publish')

        then:
        file('api/api-typescript/src/.npmrc').text.contains('registry=http://localhost:8888/')
        file('api/api-typescript/src/.npmrc').text.contains('//localhost:8888/:_authToken=atoken')
        result.wasExecuted('api:generateNpmrc')
        result.wasExecuted('api:compileTypeScript')
        result.wasExecuted('api:publishTypeScript')

        cleanup:
        server.shutdown()
    }

    def 'publishes with token'() {
        given:
        MockWebServer server = new MockWebServer()
        server.start(8888)
        // npm publish makes two requests to the registry
        server.enqueue(new MockResponse())
        server.enqueue(new MockResponse())
        file('api/build.gradle').text = """
        apply plugin: 'com.palantir.conjure'

        conjure {
            typescript {
                installGeneratesNpmrc = false // test relies on pulling from actual https://registry.npmjs.org
            }
        }

        generateNpmrc.registryUri = "http://localhost:8888"
        generateNpmrc.token = "registry-token"
        """.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully('publish')

        then:
        file('api/api-typescript/src/.npmrc').text.contains('registry=http://localhost:8888/')
        file('api/api-typescript/src/.npmrc').text.contains('//localhost:8888/:_authToken=registry-token')
        result.wasExecuted('api:generateNpmrc')
        result.wasExecuted('api:compileTypeScript')
        result.wasExecuted('api:publishTypeScript')

        cleanup:
        server.shutdown()
    }

    def 'publishes generated code with scope'() {
        given:
        MockWebServer server = new MockWebServer()
        server.start(8888)
        // generateNpmrc will make request for token
        server.enqueue(new MockResponse().setBody("{\"token\": \"atoken\"}"))
        // npm publish makes two requests to the registry
        server.enqueue(new MockResponse())
        server.enqueue(new MockResponse())
        file('api/build.gradle').text = """
        apply plugin: 'com.palantir.conjure'
        
        conjure {
            typescript {
                packageName = "@test/api"
                installGeneratesNpmrc = false // test relies on pulling from actual https://registry.npmjs.org
            }
        }

        generateNpmrc.registryUri = "http://localhost:8888"
        generateNpmrc.username = "user"
        generateNpmrc.password = "pass"
        """.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully('publish')

        then:
        file('api/api-typescript/src/.npmrc').text.contains('@test:registry=http://localhost:8888/')
        file('api/api-typescript/src/.npmrc').text.contains('//localhost:8888/:_authToken=atoken')
        result.wasExecuted('api:generateNpmrc')
        result.wasExecuted('api:compileTypeScript')
        result.wasExecuted('api:publishTypeScript')

        cleanup:
        server.shutdown()
    }

    def readResource(String name) {
        return Resources.asCharSource(Resources.getResource(name), Charset.defaultCharset()).read()
    }
}
