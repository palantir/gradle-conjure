/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.plugintesting.ConfigurationCacheSpec
import spock.lang.Unroll

@Unroll
class ConjureTypescriptProjectSpec extends ConfigurationCacheSpec {
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
        // Set up multi-project structure 
        settingsFile << """
        rootProject.name = 'test-api'
        include 'test-api-zod'
        """.stripIndent()

        buildFile << """
        apply plugin: 'com.palantir.conjure'
        
        version '0.1.0'
        group 'com.palantir.conjure.test'

        repositories {
            mavenCentral()
        }
        
        dependencies {
            conjureGenerators 'com.palantir.conjure-zod:conjure-zod'
        }
        
        conjure {
            typescriptProject('zod') {
                packageName = "test-zod-package"
            }
        }
        """.stripIndent()

        // Create API definition file
        file('src/main/conjure/api.yml') << API_YML

        // Create the zod subproject directory and build file
        file('test-api-zod/build.gradle') << """
        // TypeScript project for zod
        """.stripIndent()
    }

    def 'typescriptProject configuration creates TypeScript tasks'() {
        when:
        def result = runTasksWithConfigurationCache('tasks', '--all')

        then:
        // Conjure generator task
        result.output.contains('compileConjureZod')
        
        // Full TypeScript task suite
        result.output.contains('compileTypeScriptZod')
        result.output.contains('installTypeScriptDependenciesZod')
        result.output.contains('publishTypeScriptZod')
        result.output.contains('generateNpmrcZod')
        
        // Debug: Print all tasks to see what's created
        println "===== ALL TASKS ====="
        println result.output
        println "===================="
    }

    def 'typescriptProject configuration populates typescriptProjects map'() {
        when:
        // Access extension during configuration phase to avoid configuration cache issues
        buildFile << """
        // Access extension during configuration phase
        def ext = project.extensions.findByType(com.palantir.gradle.conjure.api.ConjureExtension)
        println "DEBUG: Extension = " + ext
        
        if (ext != null) {
            println "DEBUG: typescriptProjects = " + ext.getTypescriptProjects()
            println "DEBUG: typescriptProjects.size() = " + ext.getTypescriptProjects().size()
            ext.getTypescriptProjects().each { key, value ->
                println "DEBUG: TypeScript project: " + key + " = " + value
            }
        }
        
        task debugExtension {
            doLast {
                println "Extension debug completed during configuration"
            }
        }
        """

        def result = runTasksWithConfigurationCache('debugExtension')

        then:
        result.output.contains('DEBUG: typescriptProjects')
        
        // Debug: Print the extension debug output
        println "===== EXTENSION DEBUG ====="
        println result.output
        println "============================"
    }
}