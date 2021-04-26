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

import java.nio.file.Files
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.gradle.util.GFileUtils

class ConjureBasePluginIntegrationSpec extends IntegrationSpec {
    private static final String API_YML = """
    types:
      definitions:
        default-package: test.test.api
        objects:
          StringExample:
            fields:
              string: string
    """.stripIndent()

    def setup() {
        buildFile << """
        allprojects {
            ${applyPlugin(ConjureBasePlugin)}
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
            }

            configurations.all {
                resolutionStrategy {
                    force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                }
            }
        }
        """.stripIndent()
    }

    def 'compile conjure'() {
        when:
        file('src/main/conjure/api.yml') << API_YML

        then:
        runTasksSuccessfully('compileIr')
        file("build/conjure-ir/${moduleName}.conjure.json")
    }

    def 'correctly caches compilation'() {
        when:
        file('src/main/conjure/api.yml') << API_YML

        then:
        def result1 = runTasksSuccessfully('compileIr')
        result1.wasExecuted('extractConjure')
        result1.wasExecuted('compileIr')
        def result2 = runTasksSuccessfully('compileIr')
        result2.wasUpToDate('extractConjure')
        result2.wasUpToDate('compileIr')
    }

    def 'fails to compile invalid conjure'() {
        when:
        file('src/main/conjure/api.yml') << "foo"

        then:
        runTasksWithFailure('compileIr')
    }

    def 'compileIr can get results from the build cache'() {
        def localBuildCache = Files.createDirectories(projectDir.toPath().resolve("local-build-cache"))
        settingsFile << """
        buildCache {
            local {
                directory = file("${localBuildCache}")
                enabled = true
            }
        }
        """.stripIndent()
        file("gradle.properties") << "\norg.gradle.caching = true\n"

        when:
        file('src/main/conjure/api.yml') << API_YML

        runTasksSuccessfully('compileIr')
        GFileUtils.deleteDirectory(projectDir.toPath().resolve("build").toFile())
        ExecutionResult result = runTasksSuccessfully('compileIr', '-i')

        then:
        result.standardOutput.contains "Task :compileIr FROM-CACHE"
    }

    def 'correctly renders IR with extensions'() {
        setup:
        file('src/main/conjure/api.yml') << API_YML

        buildFile << """
            @groovy.transform.Canonical
            class SomeDataClass implements Serializable {
                String group
                String name
                String version
            }
            compileIr {
                def complexObject = new SomeDataClass(group:'group', name:'name', version:'1.0.0')
                conjureExtensions = [key1:'stringValue', key2:complexObject]
            }
        """.stripIndent()

        when:
        def result1 = runTasksSuccessfully('compileIr')

        then:
        result1.wasExecuted('compileIr')
        def actual = new File(projectDir,'build/conjure-ir/correctly-renders-IR-with-extensions.conjure.json')
        actual.exists()

        def expected = '''
  "extensions" : {
    "key1" : "stringValue",
    "key2" : {
      "group" : "group",
      "name" : "name",
      "version" : "1.0.0",
      "original-class-name" : "SomeDataClass",
      "content-hash" : "de9b15b6540097656b202e4c0a897745"
    },
    "recommended-product-dependencies" : [ ]
  }
        '''.trim()
        actual.text.contains(expected)
    }

    def 'conjure project produces consumable configuration'() {
        when:
        addSubproject("conjure-api", "apply plugin: 'java'")
        file('conjure-api/src/main/java/Test.java') << """
        class Test {}
        """.stripIndent()
        file('conjure-api/src/main/conjure/api.yml') << API_YML

        addSubproject("api-consumer", '''
        apply plugin: 'java-library'
 
        configurations {
            irConsumer {
                attributes.attribute(
                    Attribute.of('com.palantir.conjure', Usage.class),
                    project.objects.named(Usage.class, "conjure"));
            }
        }

        dependencies {
            implementation project(':conjure-api')
            irConsumer project(':conjure-api')
        }
 
        task getIr(type: Copy) {
            from configurations.irConsumer
            into "${project.buildDir}/all-ir"
        }
 
        task getJava(type: Copy) {
            from configurations.runtimeClasspath
            into "${project.buildDir}/all-java"
        }
        '''.stripIndent())

        then:
        def result = runTasksSuccessfully('getIr', 'getJava')
        result.wasExecuted(':conjure-api:compileIr')
        result.wasExecuted(':conjure-api:compileJava')
        file("build/all-ir/conjure-api.conjure.json")
        file("build/all-java/conjure-api-0.1.0.jar")
    }
}
