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

import com.palantir.gradle.plugintesting.ConfigurationCacheSpec
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

@Unroll
class ConjurePluginTest extends ConfigurationCacheSpec implements FileExists {

    def setup() {
        createFile('settings.gradle') << '''
        include 'api'
        include 'api:api-objects'
        include 'api:api-jersey'
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
                classpath 'com.palantir.baseline:gradle-baseline-java:6.25.0'
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
                    force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
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
        file("gradle.properties") << '''
            org.gradle.daemon=false
            __TESTING=true
        '''.stripIndent(true)
    }

    def 'compileConjure generates code and ir: #location'() {
        setup:
        updateSettings(prefix)

        when:
        BuildResult result = runTasksWithConfigurationCache(':api:compileConjure')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(
                ':api:compileConjure',
                ':api:compileConjureObjects',
                ':api:compileConjureJersey',
                ':api:compileConjureTypeScript',
                ':api:compileConjureUndertow',
                ':api:compileConjureDialogue',
                ':api:compileIr'
        )

        // java
        fileExists( prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java'))
        file(prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java')).text.contains('ignoreUnknown')

        // typescript
        fileExists( prefixPath(prefix, 'api-typescript/src/api/index.ts'))
        fileExists( prefixPath(prefix, 'api-typescript/src/index.ts'))
        fileExists( prefixPath(prefix, 'api-typescript/src/tsconfig.json'))
        fileExists( prefixPath(prefix, 'api-typescript/src/package.json'))
        fileExists( prefixPath(prefix, 'api-typescript/.gitignore'))
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
        BuildResult result = runTasksWithConfigurationCache(prefixProject(prefix, 'api-dialogue:dependencies'), 'check', '-s')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(
                prefixProject(prefix, 'api-objects:compileJava'),
                ':api:compileConjureObjects',
                prefixProject(prefix, 'api-jersey:compileJava'),
                ':api:compileConjureJersey',
                prefixProject(prefix, 'api-undertow:compileJava'),
                ':api:compileConjureUndertow',
                prefixProject(prefix, 'api-dialogue:compileJava'),
                ':api:compileConjureDialogue'
        )

        fileExists( prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'check code compiles with requireNotNullAuthAndBodyParams: #location'() {
        setup:
        updateSettings(prefix)

        // language=gradle
        file('api/build.gradle') << """
        conjure {
            java {
                requireNotNullAuthAndBodyParams = true
            }
        }
        """.stripIndent()

        when:
        BuildResult result = runTasksWithConfigurationCache('compileJava')

        then:
        result.task(prefixProject(prefix, 'api-objects:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureObjects').outcome == TaskOutcome.SUCCESS
        result.task(prefixProject(prefix, 'api-jersey:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureJersey').outcome == TaskOutcome.SUCCESS
        result.task(prefixProject(prefix, 'api-undertow:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureUndertow').outcome == TaskOutcome.SUCCESS
        result.task(prefixProject(prefix, 'api-dialogue:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureDialogue').outcome == TaskOutcome.SUCCESS

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'check code compiles with requireNotNullAuthAndBodyParams and jakarta: #location'() {
        setup:
        updateSettings(prefix)

        // language=gradle
        file('api/build.gradle') << """
        conjure {
            java {
                requireNotNullAuthAndBodyParams = true
                jakartaPackages = true
            }
        }
        """.stripIndent()

        when:
        BuildResult result = runTasksWithConfigurationCache('compileJava')

        then:
        result.task(prefixProject(prefix, 'api-objects:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureObjects').outcome == TaskOutcome.SUCCESS
        result.task(prefixProject(prefix, 'api-jersey:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureJersey').outcome == TaskOutcome.SUCCESS
        result.task(prefixProject(prefix, 'api-undertow:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureUndertow').outcome == TaskOutcome.SUCCESS
        result.task(prefixProject(prefix, 'api-dialogue:compileJava')).outcome == TaskOutcome.SUCCESS
        result.task(':api:compileConjureDialogue').outcome == TaskOutcome.SUCCESS

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'check cache is used: #location'() {
        setup:
        updateSettings(prefix)

        when:
        BuildResult result = runTasksWithConfigurationCache('check')
        BuildResult result2 = runTasksWithConfigurationCache('check')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(
                ':extractConjureJava',
                prefixProject(prefix, 'api-objects:compileJava'),
                ':api:compileConjureObjects',
                prefixProject(prefix, 'api-jersey:compileJava'),
                ':api:compileConjureJersey',
                prefixProject(prefix, 'api-undertow:compileJava'),
                ':api:compileConjureUndertow',
                prefixProject(prefix, 'api-dialogue:compileJava'),
                ':api:compileConjureDialogue'
        )

        result2.tasks(TaskOutcome.UP_TO_DATE)*.path.containsAll(
                ':extractConjureJava',
                prefixProject(prefix, 'api-objects:compileJava'),
                ':api:compileConjureObjects',
                prefixProject(prefix, 'api-jersey:compileJava'),
                ':api:compileConjureJersey',
                prefixProject(prefix, 'api-undertow:compileJava'),
                ':api:compileConjureUndertow',
                prefixProject(prefix, 'api-dialogue:compileJava'),
                ':api:compileConjureDialogue'
        )

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
        BuildResult result = runTasksWithConfigurationCache('--parallel', 'check', 'tasks')

        then:
        result.task(prefixProject(prefix, 'api-objects:compileJava'))
        result.task(prefixProject(prefix, 'api-jersey:compileJava'))
        result.task(':api:compileConjureJersey')

        fileExists( prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'clean cleans up build/generated/sources/conjure-*/java/main: #location'() {
        setup:
        updateSettings(prefix)

        when:
        runTasksWithConfigurationCache('compileJava')

        then:
        fileExists( prefixPath(prefix, 'api-jersey/build/generated/sources/conjure-jersey/java/main'))
        fileExists( prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main'))
        fileExists( prefixPath(prefix, 'api-undertow/build/generated/sources/conjure-undertow/java/main'))
        fileExists( prefixPath(prefix, 'api-dialogue/build/generated/sources/conjure-dialogue/java/main'))

        when:
        BuildResult result = runTasksWithConfigurationCache('clean')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(
                ':api:cleanCompileConjureJersey',
                ':api:cleanCompileConjureObjects',
                ':api:cleanCompileConjureUndertow',
                ':api:cleanCompileConjureDialogue'
        )

        !fileExists( prefixPath(prefix, 'api-jersey/build/generated/sources/conjure-jersey/java/main'))
        !fileExists( prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main'))
        !fileExists( prefixPath(prefix, 'api-undertow/build/generated/sources/conjure-undertow/java/main'))
        !fileExists( prefixPath(prefix, 'api-dialogue/build/generated/sources/conjure-dialogue/java/main'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'compileConjure creates build/conjure for root project'() {
        when:
        runTasksWithConfigurationCache('compileConjure')

        then:
        fileExists('api/build/conjure')
    }

    def 'clean cleans up build/conjure for root project'() {
        when:
        runTasksWithConfigurationCache('compileConjure')
        BuildResult result = runTasksWithConfigurationCache('clean')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.contains(':api:cleanCopyConjureSourcesIntoBuild')

        !fileExists('api/build/conjure')
    }

    def 'compileConjure does not run tasks if up to date: #location'() {
        setup:
        updateSettings(prefix)

        when:
        runTasksWithConfigurationCache("compileConjure")
        BuildResult result = runTasksWithConfigurationCache("compileConjure")

        then:
        result.tasks(TaskOutcome.UP_TO_DATE)*.path.containsAll(
                ':api:compileConjureObjects',
                ':api:compileConjureJersey',
                ':api:compileConjureTypeScript',
                ':api:compileConjureUndertow',
                ':api:compileConjureDialogue',
                ':api:copyConjureSourcesIntoBuild',
                ':api:compileIr'
        )

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'compileConjure does run tasks if not up to date: #location'() {
        setup:
        updateSettings(prefix)

        when:
        runTasksWithConfigurationCache("compileConjure")
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
        BuildResult result = runTasksWithConfigurationCache("compileConjure")

        then:
        result.tasks(TaskOutcome.UP_TO_DATE)*.path.containsAll(
                ':api:compileConjureObjects',
                ':api:compileConjureJersey',
                ':api:compileConjureTypeScript',
                ':api:compileConjureUndertow',
                ':api:compileConjureDialogue'
        )
        result.tasks(TaskOutcome.SUCCESS)*.path.contains(':api:copyConjureSourcesIntoBuild')

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
        runTasksWithConfigurationCache("copyConjureSourcesIntoBuild")
        file(path).delete()
        runTasksWithConfigurationCache("copyConjureSourcesIntoBuild")

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
        BuildResult result = runTasksWithConfigurationCache(':api:compileConjure')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(
                ':api:compileConjure',
                ':api:compileConjureJersey',
                ':api:compileConjureObjects',
                ':api:compileIr'
        )

        fileExists('api/build/conjure/internal-import.yml')
        fileExists('api/build/conjure/conjure.yml')

        // java
        file(prefixPath(prefix, 'api-jersey/build/generated/sources/conjure-jersey/java/main/test/api/service/TestServiceFoo2.java')).text.contains(
                'import test.api.internal.InternalImport;')
        fileExists( prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main/test/api/internal/InternalImport.java'))

        // typescript
        file(prefixPath(prefix, 'api-typescript/src/service/testServiceFoo2.ts')).text.contains(
                'import { IInternalImport }')

        // ir
        fileExists( "api/build/conjure-ir/api.conjure.json")

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
        BuildResult result = runTasksWithConfigurationCache(':api:compileConjure')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.containsAll(':api:compileConjure', ':api:compileConjureObjects')
        !result.tasks.contains(':api:compileConjureJersey')

        fileExists( prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java'))
        file(prefixPath(prefix, 'api-objects/build/generated/sources/conjure-objects/java/main/test/test/api/StringExample.java')).text.contains('ignoreUnknown')

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
        BuildResult result = runTasksAndFailWithConfigurationCache(':api:compileConjure')

        then:
        !result.task(':api:compileConjureJersey')

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
        runTasksWithConfigurationCache(':api:compileConjureUndertow')

        then:
        fileExists( prefixPath(prefix, 'api-undertow/build/generated/sources/conjure-undertow/java/main/test/test/api/UndertowTestServiceFoo.java'))

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'featureFlag jakartaPackages can be enabled: #location'() {
        setup:
        file('build.gradle') << """
        allprojects {
            configurations.all {
                resolutionStrategy {
                    // the jakartaPackages flag is only obeyed by conjure-java >= 7.13.0
                    force 'com.palantir.conjure.java:conjure-java:7.13.0'
                    force 'com.palantir.conjure.java:conjure-lib:7.13.0'
                    force 'com.palantir.conjure.java:conjure-undertow-lib:7.13.0'
                }
            }
        }
        """.stripIndent()
        file('api/build.gradle') << '''
        conjure {
            java {
                jakartaPackages = true
            }
        }
        '''.stripIndent()
        updateSettings(prefix)

        when:
        runTasksWithConfigurationCache(prefixProject(prefix, 'api-jersey:compileJava'))

        then:
        String generated = prefixPath(prefix, 'api-jersey/build/generated/sources/conjure-jersey/java/main/test/test/api/TestServiceFoo.java')
        fileExists(generated)
        File generatedFile = new File(projectDir, generated)
        generatedFile.text.contains("import jakarta.ws.rs.POST;")

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
        runTasksWithConfigurationCache(':api:compileConjureTypeScript')

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
        def output = runTasksWithConfigurationCacheAndCheck(':api:compileConjureTypeScript', '--info').output

        then:
        output.contains("--nodeCompatibleModules")
        output.contains("--unknownOps=Unknown")

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

        expect:
        // doesn't matter what task is run, just need to trigger project evaluation
        runTasksWithConfigurationCacheAndCheck(':tasks')

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
        BuildResult result = runTasksWithConfigurationCache(':api:compileConjure')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.contains(':api:compileConjurePostman')
        fileExists( prefixPath(prefix, 'api-postman/src/api.postman_collection.json'))
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
        runTasksWithConfigurationCacheAndCheck('compileConjure')

        where:
        location   | prefix
        'sub'      | 'api'
        'peer'     | ''
    }

    def 'compileTypeScript is run on build for circle node 0'() {
        when:
        def stdout = runTasksWithConfigurationCacheAndCheck('build', '--dry-run',
                '-P__TESTING_CIRCLE_NODE_INDEX=0').output

        then:
        stdout.contains ':api:compileTypeScript SKIPPED'
    }

    def 'compileTypeScript is not run on build for circle node 1'() {
        when:
        def stdout = runTasksWithConfigurationCacheAndCheck('build', '--dry-run',
                '-P__TESTING_CIRCLE_NODE_INDEX=1').output

        then:
        !stdout.contains(':api:compileTypeScript SKIPPED')
    }

    def 'compileTypeScript is run on build locally'() {
        when:
        // No CIRCLE_NODE_INDEX property set means local build
        def stdout = runTasksWithConfigurationCacheAndCheck('build', '--dry-run').output

        then:
        stdout.contains ':api:compileTypeScript SKIPPED'
    }

    @RestoreSystemProperties
    def 'works with checkUnusedDependencies'() {
        System.setProperty("ignoreMutableProjectStateWarnings", "true")
        buildFile << """
            allprojects { apply plugin: 'com.palantir.baseline-exact-dependencies' }
        """.stripIndent()

        expect:
        runTasks('checkUnusedDependencies', '--warning-mode=all')
    }

    @IgnoreIf({ jvm.java11Compatible })
    def 'runs on version of gradle: #version'() {
        when:
        gradleVersion = version
        BuildResult result = runTasksWithConfigurationCache('compileConjure')

        then:
        result.tasks(TaskOutcome.SUCCESS)*.path.contains(':compileConjure')

        where:
        version << ['6.1']
    }

    def 'multiple TypeScript projects API is available'() {
        setup:
        // Test just the basic API without running any tasks
        file('api/build.gradle') << '''
            apply plugin: 'com.palantir.conjure'
            
            // Test that the new API methods exist and work
            conjure {
                typescript {
                    packageName = "default-api"
                }
                
                typescriptProject('frontend') {
                    packageName = "frontend-api"
                }
                
                typescriptProject('mobile') {
                    packageName = "mobile-api"  
                }
            }
        '''.stripIndent()

        expect:
        def result = runTasksWithConfigurationCache('tasks')
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
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
        return ':' + addPrefix(prefix, project, ':')
    }

    private String addPrefix(String prefix, String path, String delimiter) {
        if (!prefix) {
            return path
        } else {
            return "${prefix}${delimiter}${path}"
        }
    }
}
