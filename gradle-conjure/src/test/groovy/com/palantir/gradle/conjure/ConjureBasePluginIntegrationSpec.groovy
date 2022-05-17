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
import org.apache.commons.io.FileUtils

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
        file("build/conjure-ir/${moduleName}.conjure.json").isFile()
    }

    def 'compile conjure with additional flags'() {
        when:
        buildFile << """
        conjure {
            parser {
                verbose = true
            }
        }
        """.stripIndent()
        file('src/main/conjure/api.yml') << API_YML

        then:
        runTasksSuccessfully('compileIr').standardOutput.contains '--verbose'
        file("build/conjure-ir/${moduleName}.conjure.json").isFile()
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
        FileUtils.deleteDirectory(projectDir.toPath().resolve("build").toFile())
        ExecutionResult result = runTasksSuccessfully('compileIr', '-i')

        then:
        result.standardOutput.contains "Task :compileIr FROM-CACHE"
    }

    def 'renders IR with extensions'() {
        setup:
        file('src/main/conjure/api.yml') << API_YML

        buildFile << """
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
        def actualFile = new File(projectDir,'build/conjure-ir/renders-IR-with-extensions.conjure.json')
        actualFile.exists()

        // Check that a few of the lines are there.
        // Checking for the whole contents and getting spacing right is fragile
        def actual = actualFile.text
        actual.contains('"key1" : "stringValue"')
        actual.contains('"key2" : {')
        actual.contains('"group" : "group"')
        actual.contains('"recommended-product-dependencies" : [ ]')
    }

    def 'renders extensions from file'() {
        setup:
        file('src/main/conjure/api.yml') << API_YML
        file('extensions.json') << '''
            {"intkey":123, 
             "stringkey":"foo",
             "key_to_override": "override_value",
             "listkey":[
                {"group":"group", "name":"name", "version":"1.0.0"}, 
                {"group":"group2", "name":"name2", "version":"1.0.0"}
                ]
             }
        '''.stripIndent()

        buildFile << """
            class SomeDataClass implements Serializable {
                String group
                String name
                String version
            }
            compileIr {
                def complexObject = new SomeDataClass(group:'group', name:'name', version:'1.0.0')
                conjureExtensions = [key_to_override:'stringValue', key2:complexObject]
                extensionsFile = file('extensions.json')
            }
        """.stripIndent()

        when:
        def result1 = runTasksSuccessfully('compileIr')

        then:
        result1.wasExecuted('compileIr')
        def actualFile = new File(projectDir,'build/conjure-ir/renders-extensions-from-file.conjure.json')
        actualFile.exists()
        def actual = actualFile.text
        actual.contains('"key_to_override" : "stringValue"')
        actual.contains('"key2" : {')
        actual.contains('"group" : "group"')
        actual.contains('"listkey" : [ {')
        actual.contains('"recommended-product-dependencies" : [ ]')
    }

    def 'renders extensions from large file'() {
        setup:
        file('src/main/conjure/api.yml') << API_YML
        StringBuilder fileContents = new StringBuilder();
        fileContents.append('''
            {"intkey":123, 
             "stringkey":"foo",
             "key_to_override": "override_value",
             "listkey":[
             '''.stripIndent())
        (1..100000).each {
            fileContents.append("{\"group\":\"group$it\", \"name\":\"name$it\", \"version\":\"$it.0.0\"}")
            if (it<100000) {
                fileContents.append(",")
            }
            fileContents.append("\n")
        }
        fileContents.append(']}')
        file('extensions.json') << fileContents.toString()

        buildFile << """
            compileIr {
                extensionsFile = file('extensions.json')
            }
        """.stripIndent()

        when:
        def result1 = runTasksSuccessfully('compileIr')

        then:
        result1.wasExecuted('compileIr')
        def actualFile = new File(projectDir,'build/conjure-ir/renders-extensions-from-large-file.conjure.json')
        actualFile.exists()
        def actual = actualFile.text
        actual.contains('"version" : "100000.0.0"')
        actual.contains('"recommended-product-dependencies" : [ ]')
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
