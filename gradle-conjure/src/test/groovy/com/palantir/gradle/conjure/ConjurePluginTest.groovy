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
import spock.lang.IgnoreIf
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

@Unroll
class ConjurePluginTest extends IntegrationSpec {

    def setup() {
        createFile('settings.gradle') << '''
        include 'api'
        include 'api:api-objects'
        include 'api:api-jersey'
        include 'api:api-retrofit'
        include 'api:api-typescript'
        include 'api:api-undertow'
        include 'api:api-dialogue'
        include 'server'
        '''.stripIndent()

        buildFile << """
        buildscript {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            dependencies {
                classpath 'com.palantir.baseline:gradle-baseline-java:4.38.0'
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
                   force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure.java:conjure-lib:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.dialogue:dialogue-target:${TestVersions.CONJURE_JAVA_DIALOG}'
                   force 'com.palantir.conjure.java:conjure-undertow-lib:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                   force 'com.palantir.conjure.typescript:conjure-typescript:${TestVersions.CONJURE_TYPESCRIPT}'

                   force 'com.fasterxml.jackson.core:jackson-annotations:2.10.2'
                   force 'com.fasterxml.jackson.core:jackson-databind:2.10.2'
                   force 'com.google.guava:guava:23.6.1-jre'
                   force 'com.palantir.safe-logging:preconditions:1.12.0'
                   force 'com.palantir.safe-logging:safe-logging:1.12.0'
                   force 'com.squareup.retrofit2:retrofit:2.1.0'
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

    def 'compileConjure generates code and ir: #location'() {
        setup:
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjure')
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(':api:compileConjureTypeScript')
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(':api:compileConjureDialogue')
        result.wasExecuted(':api:compileIr')

        // java
        fileExists(prefixPath(prefix, 'api-objects/src/generated/java/test/test/api/StringExample.java'))
        file(prefixPath(prefix, 'api-objects/src/generated/java/test/test/api/StringExample.java')).text.contains('ignoreUnknown')
        fileExists(prefixPath(prefix, 'api-objects/.gitignore'))
        file(prefixPath(prefix, 'api-objects/.gitignore')).readLines() == ['/src/generated/java/']

        // typescript
        fileExists(prefixPath(prefix, 'api-typescript/src/api/index.ts'))
        fileExists(prefixPath(prefix, 'api-typescript/src/index.ts'))
        fileExists(prefixPath(prefix, 'api-typescript/src/tsconfig.json'))
        fileExists(prefixPath(prefix, 'api-typescript/src/package.json'))
        fileExists(prefixPath(prefix, 'api-typescript/.gitignore'))
        file(prefixPath(prefix, 'api-typescript/.gitignore')).readLines() == ["/src/"]

        // irFile - these are always in api project
        fileExists('api/build/conjure-ir/api.conjure.json')
        file('api/build/conjure-ir/api.conjure.json').text.contains('TestServiceFoo')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'check code compiles: #location'() {
        setup:
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksSuccessfully(prefixProject(prefix, 'api-dialogue:dependencies'), 'check', '-s')

        then:
        result.wasExecuted(prefixProject(prefix, 'api-objects:compileJava'))
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(prefixProject(prefix, 'api-jersey:compileJava'))
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(prefixProject(prefix, 'api-retrofit:compileJava'))
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(prefixProject(prefix, 'api-undertow:compileJava'))
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(prefixProject(prefix, 'api-dialogue:compileJava'))
        result.wasExecuted(':api:compileConjureDialogue')

        fileExists(prefixPath(prefix, 'api-objects/src/generated/java/test/test/api/StringExample.java'))
        fileExists(prefixPath(prefix, 'api-objects/.gitignore'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'check cache is used: #location'() {
        setup:
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksSuccessfully('check')
        ExecutionResult result2 = runTasksSuccessfully('check')

        then:
        result.wasExecuted(':extractConjureJava')
        result.wasExecuted(prefixProject(prefix, 'api-objects:compileJava'))
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(prefixProject(prefix, 'api-jersey:compileJava'))
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(prefixProject(prefix, 'api-retrofit:compileJava'))
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(prefixProject(prefix, 'api-undertow:compileJava'))
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(prefixProject(prefix, 'api-dialogue:compileJava'))
        result.wasExecuted(':api:compileConjureDialogue')

        result2.wasUpToDate(':extractConjureJava')
        result2.wasUpToDate(prefixProject(prefix, 'api-objects:compileJava'))
        result2.wasUpToDate(':api:compileConjureObjects')
        result2.wasUpToDate(prefixProject(prefix, 'api-jersey:compileJava'))
        result2.wasUpToDate(':api:compileConjureJersey')
        result2.wasUpToDate(prefixProject(prefix, 'api-retrofit:compileJava'))
        result2.wasUpToDate(':api:compileConjureRetrofit')
        result2.wasUpToDate(prefixProject(prefix, 'api-undertow:compileJava'))
        result2.wasUpToDate(':api:compileConjureUndertow')
        result2.wasUpToDate(prefixProject(prefix, 'api-dialogue:compileJava'))
        result2.wasUpToDate(':api:compileConjureDialogue')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'check code compiles when run in parallel with multiple build targets: #location'() {
        setup:
        updateSettings(prefix)

        when:
        System.setProperty("ignoreMutableProjectStateWarnings", "true")
        ExecutionResult result = runTasksSuccessfully('--parallel', 'check', 'tasks')

        then:
        result.wasExecuted(prefixProject(prefix, 'api-objects:compileJava'))
        result.wasExecuted(prefixProject(prefix, 'api-jersey:compileJava'))
        result.wasExecuted(':api:compileConjureJersey')

        fileExists(prefixPath(prefix, 'api-objects/src/generated/java/test/test/api/StringExample.java'))
        fileExists(prefixPath(prefix, 'api-objects/.gitignore'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'clean cleans up src/generated/java: #location'() {
        setup:
        updateSettings(prefix)

        when:
        runTasksSuccessfully('compileJava')
        ExecutionResult result = runTasksSuccessfully('clean')

        then:
        result.wasExecuted(':api:cleanCompileConjureJersey')
        result.wasExecuted(':api:cleanCompileConjureObjects')
        result.wasExecuted(':api:cleanCompileConjureRetrofit')
        result.wasExecuted(':api:cleanCompileConjureUndertow')
        result.wasExecuted(':api:cleanCompileConjureDialogue')

        !fileExists(prefixPath(prefix, 'api-jersey/src/generated/java'))
        !fileExists(prefixPath(prefix, 'api-objects/src/generated/java'))
        !fileExists(prefixPath(prefix, 'api-retrofit/src/generated/java'))
        !fileExists(prefixPath(prefix, 'api-undertow/src/generated/java'))
        !fileExists(prefixPath(prefix, 'api-dialogue/src/generated/java'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'compileConjure creates build/conjure for root project'() {
        when:
        runTasksSuccessfully('compileConjure')

        then:
        fileExists('api/build/conjure')
    }

    def 'clean cleans up build/conjure for root project'() {
        when:
        runTasksSuccessfully('compileConjure')
        ExecutionResult result = runTasksSuccessfully('clean')

        then:
        result.wasExecuted(':api:cleanCopyConjureSourcesIntoBuild')

        !fileExists('api/build/conjure')
    }

    def 'compileConjure does not run tasks if up to date: #location'() {
        setup:
        updateSettings(prefix)

        when:
        runTasksSuccessfully("compileConjure")
        ExecutionResult result = runTasksSuccessfully("compileConjure")

        then:
        result.wasUpToDate(prefixProject(prefix, 'api-objects:gitignoreConjureObjects'))
        result.wasUpToDate(prefixProject(prefix, 'api-jersey:gitignoreConjureJersey'))
        result.wasUpToDate(prefixProject(prefix, 'api-retrofit:gitignoreConjureRetrofit'))
        result.wasUpToDate(prefixProject(prefix, 'api-typescript:gitignoreConjureTypeScript'))
        result.wasUpToDate(prefixProject(prefix, 'api-undertow:gitignoreConjureUndertow'))
        result.wasUpToDate(prefixProject(prefix, 'api-dialogue:gitignoreConjureDialogue'))
        result.wasUpToDate(':api:compileConjureObjects')
        result.wasUpToDate(':api:compileConjureJersey')
        result.wasUpToDate(':api:compileConjureRetrofit')
        result.wasUpToDate(':api:compileConjureTypeScript')
        result.wasUpToDate(':api:compileConjureUndertow')
        result.wasUpToDate(':api:compileConjureDialogue')
        result.wasUpToDate(':api:copyConjureSourcesIntoBuild')
        result.wasUpToDate(':api:compileIr')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'compileConjure does run tasks if not up to date: #location'() {
        setup:
        updateSettings(prefix)

        when:
        runTasksSuccessfully("compileConjure")
        createFile('api/src/main/conjure/api.yml').write '''
        types:
          definitions:
            default-package: test.test.api
            objects:
              StringExample:
                fields:
                  string: string
        services:
          TestServiceFoo:
            name: Changed name of Test Service Foo
            package: test.test.api

            endpoints:
              post:
                http: POST /post
                args:
                  object: StringExample
                returns: StringExample
        '''.stripIndent()
        ExecutionResult result = runTasksSuccessfully("compileConjure")

        then:
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(':api:compileConjureTypeScript')
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(':api:compileConjureDialogue')
        result.wasExecuted(':api:copyConjureSourcesIntoBuild')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'conjure files which no longer exist are removed from build dir'() {
        when:
        String path = 'api/src/main/conjure/todelete.yml'
        createFile(path) << '''
        types:
          definitions:
            default-package: test.a.api
            objects:
              UnionTypeExample:
                union:
                  number: integer
        '''.stripIndent()
        runTasksSuccessfully("copyConjureSourcesIntoBuild")
        file(path).delete()
        runTasksSuccessfully("copyConjureSourcesIntoBuild")

        then:
        !fileExists('api/build/conjure/todelete.yml')
    }

    def 'copies conjure imports into build directory and provides imports to conjure compiler: #location'() {
        setup:
        updateSettings(prefix)

        createFile('api/src/main/conjure/conjure.yml') << '''
        types:
          conjure-imports:
            internalImport: internal-import.yml
          definitions:
            default-package: test.api.default
            objects:

        services:
          TestServiceFoo2:
            name: Test Service Foo
            package: test.api.service

            endpoints:
              post:
                http: POST /post
                args:
                  object: internalImport.InternalImport
                returns: internalImport.InternalImport
        '''.stripIndent()

        createFile('api/src/main/conjure/internal-import.yml') << '''
        types:
          definitions:
            default-package: test.api.internal
            objects:
              InternalImport:
                fields:
                  stringField: string
        '''.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjure')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(":api:compileIr")

        fileExists('api/build/conjure/internal-import.yml')
        fileExists('api/build/conjure/conjure.yml')

        // java
        file(prefixPath(prefix, 'api-jersey/src/generated/java/test/api/service/TestServiceFoo2.java')).text.contains(
                'import test.api.internal.InternalImport;')
        file(prefixPath(prefix, 'api-retrofit/src/generated/java/test/api/service/TestServiceFoo2Retrofit.java')).text.contains(
                'import test.api.internal.InternalImport;')
        fileExists(prefixPath(prefix, 'api-objects/src/generated/java/test/api/internal/InternalImport.java'))

        // typescript
        file(prefixPath(prefix, 'api-typescript/src/service/testServiceFoo2.ts')).text.contains(
                'import { IInternalImport }')

        // ir
        fileExists("api/build/conjure-ir/api.conjure.json")

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'omitting a project from settings is sufficient to disable: #location'() {
        setup:
        file('settings.gradle').text = '''
        include 'api'
        include 'api:api-objects'
        '''.stripIndent()

        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjure')
        result.wasExecuted(':api:compileConjureObjects')
        !result.wasExecuted(':api:compileConjureJersey')

        fileExists(prefixPath(prefix, 'api-objects/src/generated/java/test/test/api/StringExample.java'))
        file(prefixPath(prefix, 'api-objects/src/generated/java/test/test/api/StringExample.java')).text.contains('ignoreUnknown')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'including only the jersey project throws because objects project is missing: #location'() {
        given:
        file('settings.gradle').text = '''
        include 'api'
        include 'api:api-jersey'
        '''.stripIndent()
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksWithFailure(':api:compileConjure')

        then:
        !result.wasExecuted(':api:compileConjureJersey')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'featureFlag UndertowServicePrefix can be enabled: #location'() {
        file('api/build.gradle') << '''
        conjure {
            java {
                undertowServicePrefixes = true
            }
        }
        '''.stripIndent()
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjureUndertow')

        then:
        fileExists(prefixPath(prefix, 'api-undertow/src/generated/java/test/test/api/UndertowTestServiceFoo.java'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'typescript extension is respected: #location'() {
        file('api/build.gradle') << '''
        conjure {
            typescript {
                packageName = "foo"
                version = "0.0.0"
                nodeCompatibleModules = true
            }
        }
        '''.stripIndent()
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjureTypeScript')

        then:
        file(prefixPath(prefix, 'api-typescript/src/package.json')).text.contains('"name": "foo"')
        file(prefixPath(prefix, 'api-typescript/src/package.json')).text.contains('"version": "0.0.0"')
        file(prefixPath(prefix, 'api-typescript/src/tsconfig.json')).text.contains('"module": "commonjs"')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'passes additional option when running compile task: #location'() {
        file('api/build.gradle') << '''
        conjure {
            typescript {
                nodeCompatibleModules = true
                unknownOps = "Unknown"
            }
        }
        '''.stripIndent()
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasks(':api:compileConjureTypeScript')

        then:
        result.standardOutput.contains("--nodeCompatibleModules")
        result.standardOutput.contains("--unknownOps=Unknown")

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'works with afterEvaluate: #location'() {
        file('build.gradle') << '''
            allprojects {
                afterEvaluate { p ->
                    if (p.tasks.findByPath('check') == null) {
                        p.tasks.create('check')
                    }
                }
            }
        '''.stripIndent()
        updateSettings(prefix)

        when:
        // doesn't matter what task is run, just need to trigger project evaluation
        ExecutionResult result = runTasksSuccessfully(':tasks')

        then:
        result.success

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'supports generic generators: #location'() {
        setup:
        addSubproject('api:api-postman')

        def apiProjectFile = file('api/build.gradle')
        apiProjectFile.text = """
            //this property is ignored in "sub" mode
            project.ext['com.palantir.conjure.generator_language_names']='postman'

            $apiProjectFile.text

            dependencies {
                conjureGenerators 'com.palantir.conjure.postman:conjure-postman:${TestVersions.CONJURE_POSTMAN}'
            }
    
            conjure {
                options "postman", {
                    productName = project.name
                    productVersion = '1.0.0'
                }
            }
            """.stripIndent()
        updateSettings(prefix)

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjurePostman')
        fileExists(prefixPath(prefix, 'api-postman/src/api.postman_collection.json'))
        file(prefixPath(prefix, 'api-postman/src/api.postman_collection.json')).text.contains('"version" : "1.0.0"')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'generic setup is a no-op if there no generic subprojects: #location'() {
        given:
        file('api/build.gradle') << """
        dependencies {
            // The following will cause configuration to fail
            conjureGenerators 'com.google.guava:guava'
        }
        """.stripIndent()
        updateSettings(prefix)

        expect:
        runTasksSuccessfully('compileConjure')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'sets up idea source sets correctly'() {
        given:
        createFile('api/api-jersey/some-extra-source-folder')

        file('build.gradle') << '''
        subprojects {
            apply plugin: 'idea'

            idea {
                module {
                    sourceDirs += file('some-extra-source-folder')
                }
            }
        }
        '''.stripIndent()

        when:
        runTasksSuccessfully('idea')

        then:
        def slurper = new XmlParser()
        def module = slurper.parse(file('api/api-jersey/api-jersey.iml'))
        def sourcesFolderUrls = module.component.content.sourceFolder.@url

        sourcesFolderUrls.size() == 2
        sourcesFolderUrls.contains('file://$MODULE_DIR$/some-extra-source-folder')
        sourcesFolderUrls.contains('file://$MODULE_DIR$/src/generated/java')
    }

    @RestoreSystemProperties
    def 'works with checkUnusedDependencies'() {
        System.setProperty("ignoreMutableProjectStateWarnings", "true")
        buildFile << """
            allprojects { apply plugin: 'com.palantir.baseline-exact-dependencies' }
        """.stripIndent()

        expect:
        runTasksSuccessfully('checkUnusedDependencies', '--warning-mode=all')
    }

    @IgnoreIf({ jvm.java11Compatible })
    def 'runs on version of gradle: #version'() {
        when:
        gradleVersion = version
        ExecutionResult result = runTasksSuccessfully('compileConjure')

        then:
        result.success

        where:
        version << ['6.1']
    }

    /**
     * Modify the location of derived projects if necessary
     */
    private void updateSettings(String prefix) {
        if (prefix != 'api') {
            def settingsFile = file('settings.gradle')
            settingsFile.text = settingsFile.text.replaceAll('api:', "${prefix}:")

            def apiProjectFile = file('api/build.gradle')
            apiProjectFile.text = '''
            project.ext['com.palantir.conjure.use_flat_project_structure']=true
            ''' + apiProjectFile.text
        }
    }

    private String prefixPath(String prefix, String path) {
        return addPrefix(prefix, path, '/')
    }

    private String prefixProject(String prefix, String project) {
        return addPrefix(prefix, project, ':')
    }

    private String addPrefix(String prefix, String path, String delimiter) {
        if (!prefix) {
            return path
        } else {
            return "${prefix}${delimiter}${path}"
        }
    }

}
