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

import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension
import com.palantir.gradle.dist.RecommendedProductDependencies

import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipFile
import nebula.test.IntegrationSpec

class ConjureServiceDependencyTest extends IntegrationSpec {

    def setup() {
        addSubproject('api')
        addSubproject('api:api-objects')
        addSubproject('api:api-jersey')
        addSubproject('api:api-undertow')
        addSubproject('api:api-typescript')

        buildFile << """
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
        file("gradle.properties") << "org.gradle.daemon=false"
    }

    def "generates empty product dependencies if not configured"() {
        when:
        runTasksSuccessfully(':api:generateConjureServiceDependencies')

        then:
        fileExists("api/build/service-dependencies.json")
        file('api/build/service-dependencies.json').text == '[]'
    }

    def "generates product dependencies if extension is configured"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        runTasksSuccessfully(':api:generateConjureServiceDependencies')

        then:
        fileExists('api/build/service-dependencies.json')
        file('api/build/service-dependencies.json').text.contains('"product-group":"com.palantir.conjure"')
        file('api/build/service-dependencies.json').text.contains('"product-name":"conjure"')
        file('api/build/service-dependencies.json').text.contains('"minimum-version":"1.2.0"')
        file('api/build/service-dependencies.json').text.contains('"maximum-version":"2.x.x"')
        file('api/build/service-dependencies.json').text.contains('"recommended-version":"1.2.0"')
    }

    def "correctly passes product dependencies to conjure"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.standardOutput.find('with args: \\[.*, --extensions, '
                + '\\{"recommended-product-dependencies":\\[\\{'
                + '"product-group":"com.palantir.conjure",'
                + '"product-name":"conjure",'
                + '"minimum-version":"1.2.0",'
                + '"maximum-version":"2.x.x",'
                + '"recommended-version":"1.2.0",'
                + '"optional":false\\}]\\}]') != null
    }

    def "custom productDependenciesTransformer can set the minimumVersion"() {
        //language=gradle
        file('api/build.gradle') << '''
        import java.util.function.Function
            
        serviceDependencies {
            productDependenciesTransformers.add(new Function<Set, Set>() {
                @Override
                Set apply(Set deps) {
                    def modified = deps.collect { dep ->
                        dep.minimumVersion = "1.1.1"
                        dep
                    }
                    return modified as Set
                }
            })
        
            serviceDependency {
                productGroup = "bar"
                productName = "foo"
                recommendedVersion = "2.0.0"
                maximumVersion = "2.0.0"
            }
        }
        
        tasks.register('printServiceDependencies'){
            doLast {
                serviceDependencies.productDependencies.orNull?.each { dep ->
                    println "minimumVersion=${dep.minimumVersion}"
                }
            }
        }
        '''.stripIndent(true)
        when: 'we check for the minimum version'
        def result = runTasksSuccessfully('printServiceDependencies')

        then: 'it is not set'
        result.standardOutput.contains("minimumVersion=nul")

        when: 'we run the jar task first'
        def resultJar = runTasksSuccessfully('jar', 'printServiceDependencies')


        then: 'the minimum version is set'
        resultJar.standardOutput.contains("minimumVersion=1.1.1")
    }

    def "correctly passes product dependencies to generators"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:generateConjureServiceDependencies')
        file('api/api-typescript/src/package.json').text.contains('sls')
    }

    def "does not pass product dependencies to java objects or undertow"() {
         file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-objects:Jar')
        def result2 = runTasksSuccessfully(':api:api-undertow:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        !result2.wasExecuted(':api:generateConjureServiceDependencies')
        readRecommendedProductDeps(file('api/api-objects/build/libs/api-objects-0.1.0.jar')) == null
        readRecommendedProductDeps(file('api/api-undertow/build/libs/api-undertow-0.1.0.jar')) == null
    }

    def "correctly configures manifest for java jersey"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
                optional = false
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        def recommendedDeps = readRecommendedProductDeps(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        recommendedDeps == '{"recommended-product-dependencies":[{' +
                '"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure",' +
                '"minimum-version":"1.2.0",' +
                '"recommended-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"optional":false}]}'
    }

    def "correctly configures manifest for java jersey with two optional dependencies"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
                optional = true
            }
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure-variant"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
                optional = true
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        def recommendedDeps = readRecommendedProductDeps(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        recommendedDeps == '{"recommended-product-dependencies":[{' +
                '"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure",' +
                '"minimum-version":"1.2.0",' +
                '"recommended-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"optional":true},' +
                '{"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure-variant",' +
                '"minimum-version":"1.2.0",' +
                '"recommended-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"optional":true}' +
                ']}'
    }

    def "fails on absent fields"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "fails on invalid version"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.x.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "fails on invalid group"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir:conjure"
                productName = "conjure"
                minimumVersion = "1.0.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "no endpoint versions attribute if no min versions configured"() {
        setup:
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        Attributes attributes = getAttributes(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        attributes.containsKey(name(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY))
        !attributes.containsKey(name(ConjureProductDependenciesExtension.ENDPOINT_VERSIONS_MANIFEST_KEY))
    }

    def "endpoint information written if configured"() {
        setup:
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
            endpointVersion {
                httpPath = "/post"
                httpMethod = "POST"
                minVersion = "0.1.0"
                maxVersion = "1.2.0"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        result.wasExecuted(':api:api-jersey:configureEndpointVersionBounds')
        Attributes attributes = getAttributes(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        def recommendedDeps = attributes.getValue(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
        //check to make sure we didn't stomp over the recommended-product-dependencies
        recommendedDeps.contains('"recommended-product-dependencies"')
        def endpointVersions = attributes.getValue(ConjureProductDependenciesExtension.ENDPOINT_VERSIONS_MANIFEST_KEY)
        endpointVersions == '{"endpoint-version-bounds":[{"http-path":"/post","http-method":"POST","min-version":"0.1.0","max-version":"1.2.0"}]}'
    }

    def "nullable endpoint maximum version bound"() {
        setup:
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
            endpointVersion {
                httpPath = "/post"
                httpMethod = "POST"
                minVersion = "0.1.0"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        result.wasExecuted(':api:api-jersey:configureEndpointVersionBounds')
        Attributes attributes = getAttributes(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        def recommendedDeps = attributes.getValue(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
        //check to make sure we didn't stomp over the recommended-product-dependencies
        recommendedDeps.contains('"recommended-product-dependencies"')
        def endpointVersions = attributes.getValue(ConjureProductDependenciesExtension.ENDPOINT_VERSIONS_MANIFEST_KEY)
        endpointVersions == '{"endpoint-version-bounds":[{"http-path":"/post","http-method":"POST","min-version":"0.1.0"}]}'
    }

    Attributes getAttributes(File jarFile) {
        def zf = new ZipFile(jarFile)
        def manifestEntry = zf.getEntry("META-INF/MANIFEST.MF")
        def manifest = new Manifest(zf.getInputStream(manifestEntry))
        return manifest.getMainAttributes()
    }

    private Attributes.Name name(String name) {
        new Attributes.Name(name)
    }

    def readRecommendedProductDeps(File jarFile) {
        return getAttributes(jarFile).getValue(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
    }
}

/*v
Using 10 worker leases.
Received JVM installation metadata from '/Users/finlayw/.gradle/gradle-jdks/amazon-corretto-17.0.15.6.1': {JAVA_HOME=/Users/finlayw/.gradle/gradle-jdks/amazon-corretto-17.0.15.6.1, JAVA_VERSION=17.0.15, JAVA_VENDOR=Amazon.com Inc., RUNTIME_NAME=OpenJDK Runtime Environment, RUNTIME_VERSION=17.0.15+6-LTS, VM_NAME=OpenJDK 64-Bit Server VM, VM_VERSION=17.0.15+6-LTS, VM_VENDOR=Amazon.com Inc., OS_ARCH=aarch64}
Watching the file system is configured to be enabled if available
File system watching is inactive
Starting Build
Settings evaluated using settings file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/settings.gradle'.
Configuring project ':api:api-objects' without an existing directory is deprecated. The configured projectDirectory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-objects' does not exist, can't be written to or is not a directory. This behavior has been deprecated. This will fail with an error in Gradle 9.0. Make sure the project directory exists and can be written. Consult the upgrading guide for further information: https://docs.gradle.org/8.14.2/userguide/upgrading_version_8.html#deprecated_missing_project_directory
	at org.gradle.initialization.DefaultSettingsLoader.emitProjectDirectoryMissingWarning(DefaultSettingsLoader.java:207)
	at org.gradle.initialization.DefaultSettingsLoader.lambda$validate$1(DefaultSettingsLoader.java:196)
	at java.base/java.lang.Iterable.forEach(Iterable.java:75)
	at org.gradle.initialization.DefaultSettingsLoader.validate(DefaultSettingsLoader.java:189)
	at org.gradle.initialization.DefaultSettingsLoader.findSettingsAndLoadIfAppropriate(DefaultSettingsLoader.java:184)
	at org.gradle.initialization.DefaultSettingsLoader.findAndLoadSettings(DefaultSettingsLoader.java:86)
	at org.gradle.initialization.SettingsAttachingSettingsLoader.findAndLoadSettings(SettingsAttachingSettingsLoader.java:33)
	at org.gradle.internal.composite.CommandLineIncludedBuildSettingsLoader.findAndLoadSettings(CommandLineIncludedBuildSettingsLoader.java:35)
	at org.gradle.internal.composite.ChildBuildRegisteringSettingsLoader.findAndLoadSettings(ChildBuildRegisteringSettingsLoader.java:44)
	at org.gradle.internal.composite.CompositeBuildSettingsLoader.findAndLoadSettings(CompositeBuildSettingsLoader.java:35)
	at org.gradle.initialization.InitScriptHandlingSettingsLoader.findAndLoadSettings(InitScriptHandlingSettingsLoader.java:33)
	at org.gradle.api.internal.initialization.CacheConfigurationsHandlingSettingsLoader.findAndLoadSettings(CacheConfigurationsHandlingSettingsLoader.java:36)
	at org.gradle.initialization.GradlePropertiesHandlingSettingsLoader.findAndLoadSettings(GradlePropertiesHandlingSettingsLoader.java:38)
	at org.gradle.initialization.DefaultSettingsPreparer.prepareSettings(DefaultSettingsPreparer.java:31)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.doLoadBuild(BuildOperationFiringSettingsPreparer.java:71)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.run(BuildOperationFiringSettingsPreparer.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer.prepareSettings(BuildOperationFiringSettingsPreparer.java:54)
	at org.gradle.initialization.VintageBuildModelController.lambda$prepareSettings$1(VintageBuildModelController.java:80)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$transitionIfNotPreviously$11(StateTransitionController.java:213)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.transitionIfNotPreviously(StateTransitionController.java:209)
	at org.gradle.initialization.VintageBuildModelController.prepareSettings(VintageBuildModelController.java:80)
	at org.gradle.initialization.VintageBuildModelController.prepareToScheduleTasks(VintageBuildModelController.java:70)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$prepareToScheduleTasks$6(DefaultBuildLifecycleController.java:175)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransition$9(StateTransitionController.java:190)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransition(StateTransitionController.java:186)
	at org.gradle.internal.build.DefaultBuildLifecycleController.prepareToScheduleTasks(DefaultBuildLifecycleController.java:173)
	at org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer.scheduleRequestedTasks(DefaultBuildTreeWorkPreparer.java:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:35)
	at org.gradle.composite.internal.DefaultIncludedBuildTaskGraph.withNewWorkGraph(DefaultIncludedBuildTaskGraph.java:112)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController.scheduleAndRunRequestedTasks(VintageBuildTreeWorkController.kt:35)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$scheduleAndRunTasks$1(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$runBuild$4(DefaultBuildTreeLifecycleController.java:120)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$6(StateTransitionController.java:169)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$7(StateTransitionController.java:169)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:46)
	at org.gradle.internal.model.StateTransitionController.transition(StateTransitionController.java:169)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.runBuild(DefaultBuildTreeLifecycleController.java:117)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:72)
	at org.gradle.tooling.internal.provider.runner.BuildModelActionRunner.run(BuildModelActionRunner.java:53)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.internal.buildtree.ProblemReportingBuildActionRunner.run(ProblemReportingBuildActionRunner.java:49)
	at org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner.run(BuildOutcomeReportingBuildActionRunner.java:71)
	at org.gradle.tooling.internal.provider.FileSystemWatchingBuildActionRunner.run(FileSystemWatchingBuildActionRunner.java:135)
	at org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.run(BuildCompletionNotifyingBuildActionRunner.java:41)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.lambda$execute$0(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:130)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.execute(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.internal.buildtree.InitDeprecationLoggingActionExecutor.execute(InitDeprecationLoggingActionExecutor.java:62)
	at org.gradle.internal.buildtree.InitProblems.execute(InitProblems.java:36)
	at org.gradle.internal.buildtree.DefaultBuildTreeContext.execute(DefaultBuildTreeContext.java:40)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.lambda$execute$0(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.internal.buildtree.BuildTreeState.run(BuildTreeState.java:60)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.execute(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:67)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor.execute(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.lambda$execute$0(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.execute(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor.execute(ContinuousBuildActionExecutor.java:110)
	at org.gradle.tooling.internal.provider.SubscribableBuildActionExecutor.execute(SubscribableBuildActionExecutor.java:64)
	at org.gradle.internal.session.DefaultBuildSessionContext.execute(DefaultBuildSessionContext.java:46)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:92)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:80)
	at org.gradle.internal.session.BuildSessionState.run(BuildSessionState.java:73)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:62)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:41)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:64)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:32)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:51)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:39)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:47)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:31)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.lambda$execute$2(ForwardStdInToThisProcess.java:79)
	at org.gradle.internal.daemon.clientinput.ClientInputForwarder.forwardInput(ClientInputForwarder.java:80)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:66)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:39)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:43)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:27)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:42)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:32)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:44)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:30)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:59)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:38)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:273)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:181)
	at org.gradle.tooling.internal.provider.DefaultConnection.getModel(DefaultConnection.java:154)
	at org.gradle.tooling.internal.consumer.connection.CancellableModelBuilderBackedModelProducer.produceModel(CancellableModelBuilderBackedModelProducer.java:53)
	at org.gradle.tooling.internal.consumer.connection.PluginClasspathInjectionSupportedCheckModelProducer.produceModel(PluginClasspathInjectionSupportedCheckModelProducer.java:38)
	at org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.run(AbstractConsumerConnection.java:64)
	at org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection.run(ParameterValidatingConsumerConnection.java:49)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:96)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:88)
	at org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor.run(LazyConsumerActionExecutor.java:143)
	at org.gradle.tooling.internal.consumer.connection.CancellableConsumerActionExecutor.run(CancellableConsumerActionExecutor.java:45)
	at org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor.run(ProgressLoggingConsumerActionExecutor.java:61)
	at org.gradle.tooling.internal.consumer.connection.RethrowingErrorsConsumerActionExecutor.run(RethrowingErrorsConsumerActionExecutor.java:38)
	at org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor$1$1.run(DefaultAsyncConsumerActionExecutor.java:66)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Configuring project ':api:api-typescript' without an existing directory is deprecated. The configured projectDirectory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-typescript' does not exist, can't be written to or is not a directory. This behavior has been deprecated. This will fail with an error in Gradle 9.0. Make sure the project directory exists and can be written. Consult the upgrading guide for further information: https://docs.gradle.org/8.14.2/userguide/upgrading_version_8.html#deprecated_missing_project_directory
	at org.gradle.initialization.DefaultSettingsLoader.emitProjectDirectoryMissingWarning(DefaultSettingsLoader.java:207)
	at org.gradle.initialization.DefaultSettingsLoader.lambda$validate$1(DefaultSettingsLoader.java:196)
	at java.base/java.lang.Iterable.forEach(Iterable.java:75)
	at org.gradle.initialization.DefaultSettingsLoader.validate(DefaultSettingsLoader.java:189)
	at org.gradle.initialization.DefaultSettingsLoader.findSettingsAndLoadIfAppropriate(DefaultSettingsLoader.java:184)
	at org.gradle.initialization.DefaultSettingsLoader.findAndLoadSettings(DefaultSettingsLoader.java:86)
	at org.gradle.initialization.SettingsAttachingSettingsLoader.findAndLoadSettings(SettingsAttachingSettingsLoader.java:33)
	at org.gradle.internal.composite.CommandLineIncludedBuildSettingsLoader.findAndLoadSettings(CommandLineIncludedBuildSettingsLoader.java:35)
	at org.gradle.internal.composite.ChildBuildRegisteringSettingsLoader.findAndLoadSettings(ChildBuildRegisteringSettingsLoader.java:44)
	at org.gradle.internal.composite.CompositeBuildSettingsLoader.findAndLoadSettings(CompositeBuildSettingsLoader.java:35)
	at org.gradle.initialization.InitScriptHandlingSettingsLoader.findAndLoadSettings(InitScriptHandlingSettingsLoader.java:33)
	at org.gradle.api.internal.initialization.CacheConfigurationsHandlingSettingsLoader.findAndLoadSettings(CacheConfigurationsHandlingSettingsLoader.java:36)
	at org.gradle.initialization.GradlePropertiesHandlingSettingsLoader.findAndLoadSettings(GradlePropertiesHandlingSettingsLoader.java:38)
	at org.gradle.initialization.DefaultSettingsPreparer.prepareSettings(DefaultSettingsPreparer.java:31)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.doLoadBuild(BuildOperationFiringSettingsPreparer.java:71)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.run(BuildOperationFiringSettingsPreparer.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer.prepareSettings(BuildOperationFiringSettingsPreparer.java:54)
	at org.gradle.initialization.VintageBuildModelController.lambda$prepareSettings$1(VintageBuildModelController.java:80)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$transitionIfNotPreviously$11(StateTransitionController.java:213)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.transitionIfNotPreviously(StateTransitionController.java:209)
	at org.gradle.initialization.VintageBuildModelController.prepareSettings(VintageBuildModelController.java:80)
	at org.gradle.initialization.VintageBuildModelController.prepareToScheduleTasks(VintageBuildModelController.java:70)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$prepareToScheduleTasks$6(DefaultBuildLifecycleController.java:175)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransition$9(StateTransitionController.java:190)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransition(StateTransitionController.java:186)
	at org.gradle.internal.build.DefaultBuildLifecycleController.prepareToScheduleTasks(DefaultBuildLifecycleController.java:173)
	at org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer.scheduleRequestedTasks(DefaultBuildTreeWorkPreparer.java:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:35)
	at org.gradle.composite.internal.DefaultIncludedBuildTaskGraph.withNewWorkGraph(DefaultIncludedBuildTaskGraph.java:112)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController.scheduleAndRunRequestedTasks(VintageBuildTreeWorkController.kt:35)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$scheduleAndRunTasks$1(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$runBuild$4(DefaultBuildTreeLifecycleController.java:120)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$6(StateTransitionController.java:169)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$7(StateTransitionController.java:169)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:46)
	at org.gradle.internal.model.StateTransitionController.transition(StateTransitionController.java:169)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.runBuild(DefaultBuildTreeLifecycleController.java:117)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:72)
	at org.gradle.tooling.internal.provider.runner.BuildModelActionRunner.run(BuildModelActionRunner.java:53)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.internal.buildtree.ProblemReportingBuildActionRunner.run(ProblemReportingBuildActionRunner.java:49)
	at org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner.run(BuildOutcomeReportingBuildActionRunner.java:71)
	at org.gradle.tooling.internal.provider.FileSystemWatchingBuildActionRunner.run(FileSystemWatchingBuildActionRunner.java:135)
	at org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.run(BuildCompletionNotifyingBuildActionRunner.java:41)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.lambda$execute$0(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:130)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.execute(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.internal.buildtree.InitDeprecationLoggingActionExecutor.execute(InitDeprecationLoggingActionExecutor.java:62)
	at org.gradle.internal.buildtree.InitProblems.execute(InitProblems.java:36)
	at org.gradle.internal.buildtree.DefaultBuildTreeContext.execute(DefaultBuildTreeContext.java:40)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.lambda$execute$0(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.internal.buildtree.BuildTreeState.run(BuildTreeState.java:60)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.execute(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:67)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor.execute(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.lambda$execute$0(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.execute(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor.execute(ContinuousBuildActionExecutor.java:110)
	at org.gradle.tooling.internal.provider.SubscribableBuildActionExecutor.execute(SubscribableBuildActionExecutor.java:64)
	at org.gradle.internal.session.DefaultBuildSessionContext.execute(DefaultBuildSessionContext.java:46)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:92)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:80)
	at org.gradle.internal.session.BuildSessionState.run(BuildSessionState.java:73)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:62)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:41)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:64)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:32)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:51)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:39)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:47)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:31)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.lambda$execute$2(ForwardStdInToThisProcess.java:79)
	at org.gradle.internal.daemon.clientinput.ClientInputForwarder.forwardInput(ClientInputForwarder.java:80)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:66)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:39)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:43)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:27)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:42)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:32)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:44)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:30)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:59)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:38)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:273)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:181)
	at org.gradle.tooling.internal.provider.DefaultConnection.getModel(DefaultConnection.java:154)
	at org.gradle.tooling.internal.consumer.connection.CancellableModelBuilderBackedModelProducer.produceModel(CancellableModelBuilderBackedModelProducer.java:53)
	at org.gradle.tooling.internal.consumer.connection.PluginClasspathInjectionSupportedCheckModelProducer.produceModel(PluginClasspathInjectionSupportedCheckModelProducer.java:38)
	at org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.run(AbstractConsumerConnection.java:64)
	at org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection.run(ParameterValidatingConsumerConnection.java:49)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:96)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:88)
	at org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor.run(LazyConsumerActionExecutor.java:143)
	at org.gradle.tooling.internal.consumer.connection.CancellableConsumerActionExecutor.run(CancellableConsumerActionExecutor.java:45)
	at org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor.run(ProgressLoggingConsumerActionExecutor.java:61)
	at org.gradle.tooling.internal.consumer.connection.RethrowingErrorsConsumerActionExecutor.run(RethrowingErrorsConsumerActionExecutor.java:38)
	at org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor$1$1.run(DefaultAsyncConsumerActionExecutor.java:66)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Configuring project ':api:api-jersey' without an existing directory is deprecated. The configured projectDirectory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-jersey' does not exist, can't be written to or is not a directory. This behavior has been deprecated. This will fail with an error in Gradle 9.0. Make sure the project directory exists and can be written. Consult the upgrading guide for further information: https://docs.gradle.org/8.14.2/userguide/upgrading_version_8.html#deprecated_missing_project_directory
	at org.gradle.initialization.DefaultSettingsLoader.emitProjectDirectoryMissingWarning(DefaultSettingsLoader.java:207)
	at org.gradle.initialization.DefaultSettingsLoader.lambda$validate$1(DefaultSettingsLoader.java:196)
	at java.base/java.lang.Iterable.forEach(Iterable.java:75)
	at org.gradle.initialization.DefaultSettingsLoader.validate(DefaultSettingsLoader.java:189)
	at org.gradle.initialization.DefaultSettingsLoader.findSettingsAndLoadIfAppropriate(DefaultSettingsLoader.java:184)
	at org.gradle.initialization.DefaultSettingsLoader.findAndLoadSettings(DefaultSettingsLoader.java:86)
	at org.gradle.initialization.SettingsAttachingSettingsLoader.findAndLoadSettings(SettingsAttachingSettingsLoader.java:33)
	at org.gradle.internal.composite.CommandLineIncludedBuildSettingsLoader.findAndLoadSettings(CommandLineIncludedBuildSettingsLoader.java:35)
	at org.gradle.internal.composite.ChildBuildRegisteringSettingsLoader.findAndLoadSettings(ChildBuildRegisteringSettingsLoader.java:44)
	at org.gradle.internal.composite.CompositeBuildSettingsLoader.findAndLoadSettings(CompositeBuildSettingsLoader.java:35)
	at org.gradle.initialization.InitScriptHandlingSettingsLoader.findAndLoadSettings(InitScriptHandlingSettingsLoader.java:33)
	at org.gradle.api.internal.initialization.CacheConfigurationsHandlingSettingsLoader.findAndLoadSettings(CacheConfigurationsHandlingSettingsLoader.java:36)
	at org.gradle.initialization.GradlePropertiesHandlingSettingsLoader.findAndLoadSettings(GradlePropertiesHandlingSettingsLoader.java:38)
	at org.gradle.initialization.DefaultSettingsPreparer.prepareSettings(DefaultSettingsPreparer.java:31)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.doLoadBuild(BuildOperationFiringSettingsPreparer.java:71)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.run(BuildOperationFiringSettingsPreparer.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer.prepareSettings(BuildOperationFiringSettingsPreparer.java:54)
	at org.gradle.initialization.VintageBuildModelController.lambda$prepareSettings$1(VintageBuildModelController.java:80)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$transitionIfNotPreviously$11(StateTransitionController.java:213)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.transitionIfNotPreviously(StateTransitionController.java:209)
	at org.gradle.initialization.VintageBuildModelController.prepareSettings(VintageBuildModelController.java:80)
	at org.gradle.initialization.VintageBuildModelController.prepareToScheduleTasks(VintageBuildModelController.java:70)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$prepareToScheduleTasks$6(DefaultBuildLifecycleController.java:175)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransition$9(StateTransitionController.java:190)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransition(StateTransitionController.java:186)
	at org.gradle.internal.build.DefaultBuildLifecycleController.prepareToScheduleTasks(DefaultBuildLifecycleController.java:173)
	at org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer.scheduleRequestedTasks(DefaultBuildTreeWorkPreparer.java:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:35)
	at org.gradle.composite.internal.DefaultIncludedBuildTaskGraph.withNewWorkGraph(DefaultIncludedBuildTaskGraph.java:112)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController.scheduleAndRunRequestedTasks(VintageBuildTreeWorkController.kt:35)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$scheduleAndRunTasks$1(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$runBuild$4(DefaultBuildTreeLifecycleController.java:120)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$6(StateTransitionController.java:169)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$7(StateTransitionController.java:169)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:46)
	at org.gradle.internal.model.StateTransitionController.transition(StateTransitionController.java:169)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.runBuild(DefaultBuildTreeLifecycleController.java:117)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:72)
	at org.gradle.tooling.internal.provider.runner.BuildModelActionRunner.run(BuildModelActionRunner.java:53)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.internal.buildtree.ProblemReportingBuildActionRunner.run(ProblemReportingBuildActionRunner.java:49)
	at org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner.run(BuildOutcomeReportingBuildActionRunner.java:71)
	at org.gradle.tooling.internal.provider.FileSystemWatchingBuildActionRunner.run(FileSystemWatchingBuildActionRunner.java:135)
	at org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.run(BuildCompletionNotifyingBuildActionRunner.java:41)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.lambda$execute$0(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:130)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.execute(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.internal.buildtree.InitDeprecationLoggingActionExecutor.execute(InitDeprecationLoggingActionExecutor.java:62)
	at org.gradle.internal.buildtree.InitProblems.execute(InitProblems.java:36)
	at org.gradle.internal.buildtree.DefaultBuildTreeContext.execute(DefaultBuildTreeContext.java:40)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.lambda$execute$0(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.internal.buildtree.BuildTreeState.run(BuildTreeState.java:60)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.execute(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:67)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor.execute(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.lambda$execute$0(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.execute(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor.execute(ContinuousBuildActionExecutor.java:110)
	at org.gradle.tooling.internal.provider.SubscribableBuildActionExecutor.execute(SubscribableBuildActionExecutor.java:64)
	at org.gradle.internal.session.DefaultBuildSessionContext.execute(DefaultBuildSessionContext.java:46)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:92)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:80)
	at org.gradle.internal.session.BuildSessionState.run(BuildSessionState.java:73)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:62)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:41)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:64)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:32)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:51)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:39)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:47)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:31)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.lambda$execute$2(ForwardStdInToThisProcess.java:79)
	at org.gradle.internal.daemon.clientinput.ClientInputForwarder.forwardInput(ClientInputForwarder.java:80)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:66)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:39)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:43)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:27)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:42)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:32)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:44)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:30)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:59)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:38)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:273)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:181)
	at org.gradle.tooling.internal.provider.DefaultConnection.getModel(DefaultConnection.java:154)
	at org.gradle.tooling.internal.consumer.connection.CancellableModelBuilderBackedModelProducer.produceModel(CancellableModelBuilderBackedModelProducer.java:53)
	at org.gradle.tooling.internal.consumer.connection.PluginClasspathInjectionSupportedCheckModelProducer.produceModel(PluginClasspathInjectionSupportedCheckModelProducer.java:38)
	at org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.run(AbstractConsumerConnection.java:64)
	at org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection.run(ParameterValidatingConsumerConnection.java:49)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:96)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:88)
	at org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor.run(LazyConsumerActionExecutor.java:143)
	at org.gradle.tooling.internal.consumer.connection.CancellableConsumerActionExecutor.run(CancellableConsumerActionExecutor.java:45)
	at org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor.run(ProgressLoggingConsumerActionExecutor.java:61)
	at org.gradle.tooling.internal.consumer.connection.RethrowingErrorsConsumerActionExecutor.run(RethrowingErrorsConsumerActionExecutor.java:38)
	at org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor$1$1.run(DefaultAsyncConsumerActionExecutor.java:66)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Configuring project ':api:api-undertow' without an existing directory is deprecated. The configured projectDirectory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-undertow' does not exist, can't be written to or is not a directory. This behavior has been deprecated. This will fail with an error in Gradle 9.0. Make sure the project directory exists and can be written. Consult the upgrading guide for further information: https://docs.gradle.org/8.14.2/userguide/upgrading_version_8.html#deprecated_missing_project_directory
	at org.gradle.initialization.DefaultSettingsLoader.emitProjectDirectoryMissingWarning(DefaultSettingsLoader.java:207)
	at org.gradle.initialization.DefaultSettingsLoader.lambda$validate$1(DefaultSettingsLoader.java:196)
	at java.base/java.lang.Iterable.forEach(Iterable.java:75)
	at org.gradle.initialization.DefaultSettingsLoader.validate(DefaultSettingsLoader.java:189)
	at org.gradle.initialization.DefaultSettingsLoader.findSettingsAndLoadIfAppropriate(DefaultSettingsLoader.java:184)
	at org.gradle.initialization.DefaultSettingsLoader.findAndLoadSettings(DefaultSettingsLoader.java:86)
	at org.gradle.initialization.SettingsAttachingSettingsLoader.findAndLoadSettings(SettingsAttachingSettingsLoader.java:33)
	at org.gradle.internal.composite.CommandLineIncludedBuildSettingsLoader.findAndLoadSettings(CommandLineIncludedBuildSettingsLoader.java:35)
	at org.gradle.internal.composite.ChildBuildRegisteringSettingsLoader.findAndLoadSettings(ChildBuildRegisteringSettingsLoader.java:44)
	at org.gradle.internal.composite.CompositeBuildSettingsLoader.findAndLoadSettings(CompositeBuildSettingsLoader.java:35)
	at org.gradle.initialization.InitScriptHandlingSettingsLoader.findAndLoadSettings(InitScriptHandlingSettingsLoader.java:33)
	at org.gradle.api.internal.initialization.CacheConfigurationsHandlingSettingsLoader.findAndLoadSettings(CacheConfigurationsHandlingSettingsLoader.java:36)
	at org.gradle.initialization.GradlePropertiesHandlingSettingsLoader.findAndLoadSettings(GradlePropertiesHandlingSettingsLoader.java:38)
	at org.gradle.initialization.DefaultSettingsPreparer.prepareSettings(DefaultSettingsPreparer.java:31)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.doLoadBuild(BuildOperationFiringSettingsPreparer.java:71)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer$LoadBuild.run(BuildOperationFiringSettingsPreparer.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.initialization.BuildOperationFiringSettingsPreparer.prepareSettings(BuildOperationFiringSettingsPreparer.java:54)
	at org.gradle.initialization.VintageBuildModelController.lambda$prepareSettings$1(VintageBuildModelController.java:80)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$transitionIfNotPreviously$11(StateTransitionController.java:213)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.transitionIfNotPreviously(StateTransitionController.java:209)
	at org.gradle.initialization.VintageBuildModelController.prepareSettings(VintageBuildModelController.java:80)
	at org.gradle.initialization.VintageBuildModelController.prepareToScheduleTasks(VintageBuildModelController.java:70)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$prepareToScheduleTasks$6(DefaultBuildLifecycleController.java:175)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransition$9(StateTransitionController.java:190)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransition(StateTransitionController.java:186)
	at org.gradle.internal.build.DefaultBuildLifecycleController.prepareToScheduleTasks(DefaultBuildLifecycleController.java:173)
	at org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer.scheduleRequestedTasks(DefaultBuildTreeWorkPreparer.java:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:35)
	at org.gradle.composite.internal.DefaultIncludedBuildTaskGraph.withNewWorkGraph(DefaultIncludedBuildTaskGraph.java:112)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController.scheduleAndRunRequestedTasks(VintageBuildTreeWorkController.kt:35)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$scheduleAndRunTasks$1(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$runBuild$4(DefaultBuildTreeLifecycleController.java:120)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$6(StateTransitionController.java:169)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$7(StateTransitionController.java:169)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:46)
	at org.gradle.internal.model.StateTransitionController.transition(StateTransitionController.java:169)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.runBuild(DefaultBuildTreeLifecycleController.java:117)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:72)
	at org.gradle.tooling.internal.provider.runner.BuildModelActionRunner.run(BuildModelActionRunner.java:53)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.internal.buildtree.ProblemReportingBuildActionRunner.run(ProblemReportingBuildActionRunner.java:49)
	at org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner.run(BuildOutcomeReportingBuildActionRunner.java:71)
	at org.gradle.tooling.internal.provider.FileSystemWatchingBuildActionRunner.run(FileSystemWatchingBuildActionRunner.java:135)
	at org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.run(BuildCompletionNotifyingBuildActionRunner.java:41)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.lambda$execute$0(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:130)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.execute(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.internal.buildtree.InitDeprecationLoggingActionExecutor.execute(InitDeprecationLoggingActionExecutor.java:62)
	at org.gradle.internal.buildtree.InitProblems.execute(InitProblems.java:36)
	at org.gradle.internal.buildtree.DefaultBuildTreeContext.execute(DefaultBuildTreeContext.java:40)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.lambda$execute$0(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.internal.buildtree.BuildTreeState.run(BuildTreeState.java:60)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.execute(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:67)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor.execute(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.lambda$execute$0(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.execute(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor.execute(ContinuousBuildActionExecutor.java:110)
	at org.gradle.tooling.internal.provider.SubscribableBuildActionExecutor.execute(SubscribableBuildActionExecutor.java:64)
	at org.gradle.internal.session.DefaultBuildSessionContext.execute(DefaultBuildSessionContext.java:46)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:92)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:80)
	at org.gradle.internal.session.BuildSessionState.run(BuildSessionState.java:73)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:62)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:41)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:64)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:32)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:51)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:39)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:47)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:31)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.lambda$execute$2(ForwardStdInToThisProcess.java:79)
	at org.gradle.internal.daemon.clientinput.ClientInputForwarder.forwardInput(ClientInputForwarder.java:80)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:66)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:39)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:43)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:27)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:42)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:32)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:44)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:30)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:59)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:38)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:273)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:181)
	at org.gradle.tooling.internal.provider.DefaultConnection.getModel(DefaultConnection.java:154)
	at org.gradle.tooling.internal.consumer.connection.CancellableModelBuilderBackedModelProducer.produceModel(CancellableModelBuilderBackedModelProducer.java:53)
	at org.gradle.tooling.internal.consumer.connection.PluginClasspathInjectionSupportedCheckModelProducer.produceModel(PluginClasspathInjectionSupportedCheckModelProducer.java:38)
	at org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.run(AbstractConsumerConnection.java:64)
	at org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection.run(ParameterValidatingConsumerConnection.java:49)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:96)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:88)
	at org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor.run(LazyConsumerActionExecutor.java:143)
	at org.gradle.tooling.internal.consumer.connection.CancellableConsumerActionExecutor.run(CancellableConsumerActionExecutor.java:45)
	at org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor.run(ProgressLoggingConsumerActionExecutor.java:61)
	at org.gradle.tooling.internal.consumer.connection.RethrowingErrorsConsumerActionExecutor.run(RethrowingErrorsConsumerActionExecutor.java:38)
	at org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor$1$1.run(DefaultAsyncConsumerActionExecutor.java:66)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Projects loaded. Root project using build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle'.
Included projects: [root project 'custom-productDependenciesTransformer', project ':api', project ':api:api-jersey', project ':api:api-objects', project ':api:api-typescript', project ':api:api-undertow']

> Configure project :
Evaluating root project 'custom-productDependenciesTransformer' using build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle'.
Build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle': line 10
Properties should be assigned using the 'propName = value' syntax. Setting a property via the Gradle-generated 'propName value' or 'propName(value)' syntax in Groovy DSL has been deprecated. This is scheduled to be removed in Gradle 10.0. Use assignment ('version = <value>') instead. Consult the upgrading guide for further information: https://docs.gradle.org/8.14.2/userguide/upgrading_version_8.html#groovy_space_assignment_syntax
	at org.gradle.internal.instantiation.generator.AsmBackedClassGenerator.logGroovySpaceAssignmentDeprecation(AsmBackedClassGenerator.java:273)
	at org.gradle.api.internal.project.DefaultProject_Decorated.version(Unknown Source)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:323)
	at org.gradle.api.internal.project.MutableStateAccessAwareDynamicObject.tryInvokeMethod(MutableStateAccessAwareDynamicObject.java:111)
	at org.gradle.internal.extensibility.MixInClosurePropertiesAsMethodsDynamicObject.tryInvokeMethod(MixInClosurePropertiesAsMethodsDynamicObject.java:38)
	at build_ewl89ije5mdjicww7b93t1xfb$_run_closure1.doCall$original(/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle:10)
	at build_ewl89ije5mdjicww7b93t1xfb$_run_closure1.doCall(/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:323)
	at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1030)
	at groovy.lang.Closure.call(Closure.java:427)
	at groovy.lang.Closure.call(Closure.java:416)
	at org.gradle.util.internal.ClosureBackedAction.execute(ClosureBackedAction.java:73)
	at org.gradle.util.internal.ConfigureUtil.configureTarget(ConfigureUtil.java:166)
	at org.gradle.util.internal.ConfigureUtil.configure(ConfigureUtil.java:107)
	at org.gradle.util.internal.ConfigureUtil$WrappedConfigureAction.execute(ConfigureUtil.java:178)
	at org.gradle.api.internal.DefaultMutationGuard$1.execute(DefaultMutationGuard.java:66)
	at org.gradle.internal.Actions.with(Actions.java:206)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator$1.run(BuildOperationCrossProjectConfigurator.java:68)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.lambda$runProjectConfigureAction$0(BuildOperationCrossProjectConfigurator.java:65)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$applyToMutableState$1(DefaultProjectStateRegistry.java:435)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.fromMutableState(DefaultProjectStateRegistry.java:453)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.applyToMutableState(DefaultProjectStateRegistry.java:434)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.runProjectConfigureAction(BuildOperationCrossProjectConfigurator.java:65)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.access$100(BuildOperationCrossProjectConfigurator.java:31)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator$BlockConfigureBuildOperation.run(BuildOperationCrossProjectConfigurator.java:106)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.runBlockConfigureAction(BuildOperationCrossProjectConfigurator.java:61)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.allprojects(BuildOperationCrossProjectConfigurator.java:52)
	at org.gradle.api.internal.project.DefaultProject.allprojects(DefaultProject.java:755)
	at org.gradle.api.internal.project.DefaultProject.allprojects(DefaultProject.java:745)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:323)
	at org.gradle.api.internal.project.MutableStateAccessAwareDynamicObject.tryInvokeMethod(MutableStateAccessAwareDynamicObject.java:111)
	at org.gradle.internal.extensibility.MixInClosurePropertiesAsMethodsDynamicObject.tryInvokeMethod(MixInClosurePropertiesAsMethodsDynamicObject.java:38)
	at org.gradle.groovy.scripts.BasicScript$ScriptDynamicObject.tryInvokeMethod(BasicScript.java:138)
	at org.gradle.api.internal.project.DefaultDynamicLookupRoutine.invokeMethod(DefaultDynamicLookupRoutine.java:58)
	at org.gradle.groovy.scripts.BasicScript.invokeMethod(BasicScript.java:87)
	at build_ewl89ije5mdjicww7b93t1xfb.run(/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle:9)
	at org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java:91)
	at org.gradle.configuration.DefaultScriptPluginFactory$ScriptPluginImpl.lambda$apply$1(DefaultScriptPluginFactory.java:141)
	at org.gradle.configuration.ProjectScriptTarget.addConfiguration(ProjectScriptTarget.java:79)
	at org.gradle.configuration.DefaultScriptPluginFactory$ScriptPluginImpl.apply(DefaultScriptPluginFactory.java:144)
	at org.gradle.configuration.BuildOperationScriptPlugin$1.run(BuildOperationScriptPlugin.java:68)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.configuration.BuildOperationScriptPlugin.lambda$apply$0(BuildOperationScriptPlugin.java:65)
	at org.gradle.internal.code.DefaultUserCodeApplicationContext.apply(DefaultUserCodeApplicationContext.java:44)
	at org.gradle.configuration.BuildOperationScriptPlugin.apply(BuildOperationScriptPlugin.java:65)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$applyToMutableState$1(DefaultProjectStateRegistry.java:435)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.fromMutableState(DefaultProjectStateRegistry.java:453)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.applyToMutableState(DefaultProjectStateRegistry.java:434)
	at org.gradle.configuration.project.BuildScriptProcessor.execute(BuildScriptProcessor.java:46)
	at org.gradle.configuration.project.BuildScriptProcessor.execute(BuildScriptProcessor.java:27)
	at org.gradle.configuration.project.ConfigureActionsProjectEvaluator.evaluate(ConfigureActionsProjectEvaluator.java:35)
	at org.gradle.configuration.project.LifecycleProjectEvaluator$EvaluateProject.lambda$run$0(LifecycleProjectEvaluator.java:109)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$applyToMutableState$1(DefaultProjectStateRegistry.java:435)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$fromMutableState$2(DefaultProjectStateRegistry.java:458)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withReplacedLocks(DefaultWorkerLeaseService.java:359)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.fromMutableState(DefaultProjectStateRegistry.java:458)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.applyToMutableState(DefaultProjectStateRegistry.java:434)
	at org.gradle.configuration.project.LifecycleProjectEvaluator$EvaluateProject.run(LifecycleProjectEvaluator.java:100)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.configuration.project.LifecycleProjectEvaluator.evaluate(LifecycleProjectEvaluator.java:72)
	at org.gradle.api.internal.project.DefaultProject.evaluateUnchecked(DefaultProject.java:827)
	at org.gradle.api.internal.project.ProjectLifecycleController.lambda$ensureSelfConfigured$2(ProjectLifecycleController.java:88)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransitionIfNotCurrentlyTransitioning$10(StateTransitionController.java:199)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransitionIfNotCurrentlyTransitioning(StateTransitionController.java:195)
	at org.gradle.api.internal.project.ProjectLifecycleController.ensureSelfConfigured(ProjectLifecycleController.java:88)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.ensureConfigured(DefaultProjectStateRegistry.java:400)
	at org.gradle.execution.TaskPathProjectEvaluator.configure(TaskPathProjectEvaluator.java:70)
	at org.gradle.execution.TaskPathProjectEvaluator.configureHierarchy(TaskPathProjectEvaluator.java:84)
	at org.gradle.configuration.DefaultProjectsPreparer.prepareProjects(DefaultProjectsPreparer.java:50)
	at org.gradle.configuration.BuildTreePreparingProjectsPreparer.prepareProjects(BuildTreePreparingProjectsPreparer.java:65)
	at org.gradle.configuration.BuildOperationFiringProjectsPreparer$ConfigureBuild.run(BuildOperationFiringProjectsPreparer.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.configuration.BuildOperationFiringProjectsPreparer.prepareProjects(BuildOperationFiringProjectsPreparer.java:40)
	at org.gradle.initialization.VintageBuildModelController.lambda$prepareProjects$2(VintageBuildModelController.java:84)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$transitionIfNotPreviously$11(StateTransitionController.java:213)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.transitionIfNotPreviously(StateTransitionController.java:209)
	at org.gradle.initialization.VintageBuildModelController.prepareProjects(VintageBuildModelController.java:84)
	at org.gradle.initialization.VintageBuildModelController.prepareToScheduleTasks(VintageBuildModelController.java:71)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$prepareToScheduleTasks$6(DefaultBuildLifecycleController.java:175)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransition$9(StateTransitionController.java:190)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransition(StateTransitionController.java:186)
	at org.gradle.internal.build.DefaultBuildLifecycleController.prepareToScheduleTasks(DefaultBuildLifecycleController.java:173)
	at org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer.scheduleRequestedTasks(DefaultBuildTreeWorkPreparer.java:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:35)
	at org.gradle.composite.internal.DefaultIncludedBuildTaskGraph.withNewWorkGraph(DefaultIncludedBuildTaskGraph.java:112)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController.scheduleAndRunRequestedTasks(VintageBuildTreeWorkController.kt:35)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$scheduleAndRunTasks$1(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$runBuild$4(DefaultBuildTreeLifecycleController.java:120)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$6(StateTransitionController.java:169)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$7(StateTransitionController.java:169)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:46)
	at org.gradle.internal.model.StateTransitionController.transition(StateTransitionController.java:169)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.runBuild(DefaultBuildTreeLifecycleController.java:117)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:72)
	at org.gradle.tooling.internal.provider.runner.BuildModelActionRunner.run(BuildModelActionRunner.java:53)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.internal.buildtree.ProblemReportingBuildActionRunner.run(ProblemReportingBuildActionRunner.java:49)
	at org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner.run(BuildOutcomeReportingBuildActionRunner.java:71)
	at org.gradle.tooling.internal.provider.FileSystemWatchingBuildActionRunner.run(FileSystemWatchingBuildActionRunner.java:135)
	at org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.run(BuildCompletionNotifyingBuildActionRunner.java:41)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.lambda$execute$0(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:130)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.execute(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.internal.buildtree.InitDeprecationLoggingActionExecutor.execute(InitDeprecationLoggingActionExecutor.java:62)
	at org.gradle.internal.buildtree.InitProblems.execute(InitProblems.java:36)
	at org.gradle.internal.buildtree.DefaultBuildTreeContext.execute(DefaultBuildTreeContext.java:40)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.lambda$execute$0(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.internal.buildtree.BuildTreeState.run(BuildTreeState.java:60)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.execute(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:67)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor.execute(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.lambda$execute$0(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.execute(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor.execute(ContinuousBuildActionExecutor.java:110)
	at org.gradle.tooling.internal.provider.SubscribableBuildActionExecutor.execute(SubscribableBuildActionExecutor.java:64)
	at org.gradle.internal.session.DefaultBuildSessionContext.execute(DefaultBuildSessionContext.java:46)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:92)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:80)
	at org.gradle.internal.session.BuildSessionState.run(BuildSessionState.java:73)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:62)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:41)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:64)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:32)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:51)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:39)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:47)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:31)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.lambda$execute$2(ForwardStdInToThisProcess.java:79)
	at org.gradle.internal.daemon.clientinput.ClientInputForwarder.forwardInput(ClientInputForwarder.java:80)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:66)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:39)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:43)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:27)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:42)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:32)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:44)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:30)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:59)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:38)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:273)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:181)
	at org.gradle.tooling.internal.provider.DefaultConnection.getModel(DefaultConnection.java:154)
	at org.gradle.tooling.internal.consumer.connection.CancellableModelBuilderBackedModelProducer.produceModel(CancellableModelBuilderBackedModelProducer.java:53)
	at org.gradle.tooling.internal.consumer.connection.PluginClasspathInjectionSupportedCheckModelProducer.produceModel(PluginClasspathInjectionSupportedCheckModelProducer.java:38)
	at org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.run(AbstractConsumerConnection.java:64)
	at org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection.run(ParameterValidatingConsumerConnection.java:49)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:96)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:88)
	at org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor.run(LazyConsumerActionExecutor.java:143)
	at org.gradle.tooling.internal.consumer.connection.CancellableConsumerActionExecutor.run(CancellableConsumerActionExecutor.java:45)
	at org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor.run(ProgressLoggingConsumerActionExecutor.java:61)
	at org.gradle.tooling.internal.consumer.connection.RethrowingErrorsConsumerActionExecutor.run(RethrowingErrorsConsumerActionExecutor.java:38)
	at org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor$1$1.run(DefaultAsyncConsumerActionExecutor.java:66)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle': line 11
Properties should be assigned using the 'propName = value' syntax. Setting a property via the Gradle-generated 'propName value' or 'propName(value)' syntax in Groovy DSL has been deprecated. This is scheduled to be removed in Gradle 10.0. Use assignment ('group = <value>') instead. Consult the upgrading guide for further information: https://docs.gradle.org/8.14.2/userguide/upgrading_version_8.html#groovy_space_assignment_syntax
	at org.gradle.internal.instantiation.generator.AsmBackedClassGenerator.logGroovySpaceAssignmentDeprecation(AsmBackedClassGenerator.java:273)
	at org.gradle.api.internal.project.DefaultProject_Decorated.group(Unknown Source)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:323)
	at org.gradle.api.internal.project.MutableStateAccessAwareDynamicObject.tryInvokeMethod(MutableStateAccessAwareDynamicObject.java:111)
	at org.gradle.internal.extensibility.MixInClosurePropertiesAsMethodsDynamicObject.tryInvokeMethod(MixInClosurePropertiesAsMethodsDynamicObject.java:38)
	at build_ewl89ije5mdjicww7b93t1xfb$_run_closure1.doCall$original(/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle:11)
	at build_ewl89ije5mdjicww7b93t1xfb$_run_closure1.doCall(/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:323)
	at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1030)
	at groovy.lang.Closure.call(Closure.java:427)
	at groovy.lang.Closure.call(Closure.java:416)
	at org.gradle.util.internal.ClosureBackedAction.execute(ClosureBackedAction.java:73)
	at org.gradle.util.internal.ConfigureUtil.configureTarget(ConfigureUtil.java:166)
	at org.gradle.util.internal.ConfigureUtil.configure(ConfigureUtil.java:107)
	at org.gradle.util.internal.ConfigureUtil$WrappedConfigureAction.execute(ConfigureUtil.java:178)
	at org.gradle.api.internal.DefaultMutationGuard$1.execute(DefaultMutationGuard.java:66)
	at org.gradle.internal.Actions.with(Actions.java:206)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator$1.run(BuildOperationCrossProjectConfigurator.java:68)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.lambda$runProjectConfigureAction$0(BuildOperationCrossProjectConfigurator.java:65)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$applyToMutableState$1(DefaultProjectStateRegistry.java:435)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.fromMutableState(DefaultProjectStateRegistry.java:453)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.applyToMutableState(DefaultProjectStateRegistry.java:434)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.runProjectConfigureAction(BuildOperationCrossProjectConfigurator.java:65)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.access$100(BuildOperationCrossProjectConfigurator.java:31)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator$BlockConfigureBuildOperation.run(BuildOperationCrossProjectConfigurator.java:106)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.runBlockConfigureAction(BuildOperationCrossProjectConfigurator.java:61)
	at org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator.allprojects(BuildOperationCrossProjectConfigurator.java:52)
	at org.gradle.api.internal.project.DefaultProject.allprojects(DefaultProject.java:755)
	at org.gradle.api.internal.project.DefaultProject.allprojects(DefaultProject.java:745)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:323)
	at org.gradle.api.internal.project.MutableStateAccessAwareDynamicObject.tryInvokeMethod(MutableStateAccessAwareDynamicObject.java:111)
	at org.gradle.internal.extensibility.MixInClosurePropertiesAsMethodsDynamicObject.tryInvokeMethod(MixInClosurePropertiesAsMethodsDynamicObject.java:38)
	at org.gradle.groovy.scripts.BasicScript$ScriptDynamicObject.tryInvokeMethod(BasicScript.java:138)
	at org.gradle.api.internal.project.DefaultDynamicLookupRoutine.invokeMethod(DefaultDynamicLookupRoutine.java:58)
	at org.gradle.groovy.scripts.BasicScript.invokeMethod(BasicScript.java:87)
	at build_ewl89ije5mdjicww7b93t1xfb.run(/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build.gradle:9)
	at org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java:91)
	at org.gradle.configuration.DefaultScriptPluginFactory$ScriptPluginImpl.lambda$apply$1(DefaultScriptPluginFactory.java:141)
	at org.gradle.configuration.ProjectScriptTarget.addConfiguration(ProjectScriptTarget.java:79)
	at org.gradle.configuration.DefaultScriptPluginFactory$ScriptPluginImpl.apply(DefaultScriptPluginFactory.java:144)
	at org.gradle.configuration.BuildOperationScriptPlugin$1.run(BuildOperationScriptPlugin.java:68)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.configuration.BuildOperationScriptPlugin.lambda$apply$0(BuildOperationScriptPlugin.java:65)
	at org.gradle.internal.code.DefaultUserCodeApplicationContext.apply(DefaultUserCodeApplicationContext.java:44)
	at org.gradle.configuration.BuildOperationScriptPlugin.apply(BuildOperationScriptPlugin.java:65)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$applyToMutableState$1(DefaultProjectStateRegistry.java:435)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.fromMutableState(DefaultProjectStateRegistry.java:453)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.applyToMutableState(DefaultProjectStateRegistry.java:434)
	at org.gradle.configuration.project.BuildScriptProcessor.execute(BuildScriptProcessor.java:46)
	at org.gradle.configuration.project.BuildScriptProcessor.execute(BuildScriptProcessor.java:27)
	at org.gradle.configuration.project.ConfigureActionsProjectEvaluator.evaluate(ConfigureActionsProjectEvaluator.java:35)
	at org.gradle.configuration.project.LifecycleProjectEvaluator$EvaluateProject.lambda$run$0(LifecycleProjectEvaluator.java:109)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$applyToMutableState$1(DefaultProjectStateRegistry.java:435)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.lambda$fromMutableState$2(DefaultProjectStateRegistry.java:458)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withReplacedLocks(DefaultWorkerLeaseService.java:359)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.fromMutableState(DefaultProjectStateRegistry.java:458)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.applyToMutableState(DefaultProjectStateRegistry.java:434)
	at org.gradle.configuration.project.LifecycleProjectEvaluator$EvaluateProject.run(LifecycleProjectEvaluator.java:100)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.configuration.project.LifecycleProjectEvaluator.evaluate(LifecycleProjectEvaluator.java:72)
	at org.gradle.api.internal.project.DefaultProject.evaluateUnchecked(DefaultProject.java:827)
	at org.gradle.api.internal.project.ProjectLifecycleController.lambda$ensureSelfConfigured$2(ProjectLifecycleController.java:88)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransitionIfNotCurrentlyTransitioning$10(StateTransitionController.java:199)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransitionIfNotCurrentlyTransitioning(StateTransitionController.java:195)
	at org.gradle.api.internal.project.ProjectLifecycleController.ensureSelfConfigured(ProjectLifecycleController.java:88)
	at org.gradle.api.internal.project.DefaultProjectStateRegistry$ProjectStateImpl.ensureConfigured(DefaultProjectStateRegistry.java:400)
	at org.gradle.execution.TaskPathProjectEvaluator.configure(TaskPathProjectEvaluator.java:70)
	at org.gradle.execution.TaskPathProjectEvaluator.configureHierarchy(TaskPathProjectEvaluator.java:84)
	at org.gradle.configuration.DefaultProjectsPreparer.prepareProjects(DefaultProjectsPreparer.java:50)
	at org.gradle.configuration.BuildTreePreparingProjectsPreparer.prepareProjects(BuildTreePreparingProjectsPreparer.java:65)
	at org.gradle.configuration.BuildOperationFiringProjectsPreparer$ConfigureBuild.run(BuildOperationFiringProjectsPreparer.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.configuration.BuildOperationFiringProjectsPreparer.prepareProjects(BuildOperationFiringProjectsPreparer.java:40)
	at org.gradle.initialization.VintageBuildModelController.lambda$prepareProjects$2(VintageBuildModelController.java:84)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$transitionIfNotPreviously$11(StateTransitionController.java:213)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.transitionIfNotPreviously(StateTransitionController.java:209)
	at org.gradle.initialization.VintageBuildModelController.prepareProjects(VintageBuildModelController.java:84)
	at org.gradle.initialization.VintageBuildModelController.prepareToScheduleTasks(VintageBuildModelController.java:71)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$prepareToScheduleTasks$6(DefaultBuildLifecycleController.java:175)
	at org.gradle.internal.model.StateTransitionController.lambda$doTransition$14(StateTransitionController.java:255)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:254)
	at org.gradle.internal.model.StateTransitionController.lambda$maybeTransition$9(StateTransitionController.java:190)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:36)
	at org.gradle.internal.model.StateTransitionController.maybeTransition(StateTransitionController.java:186)
	at org.gradle.internal.build.DefaultBuildLifecycleController.prepareToScheduleTasks(DefaultBuildLifecycleController.java:173)
	at org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer.scheduleRequestedTasks(DefaultBuildTreeWorkPreparer.java:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:36)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController$scheduleAndRunRequestedTasks$1.apply(VintageBuildTreeWorkController.kt:35)
	at org.gradle.composite.internal.DefaultIncludedBuildTaskGraph.withNewWorkGraph(DefaultIncludedBuildTaskGraph.java:112)
	at org.gradle.internal.cc.impl.VintageBuildTreeWorkController.scheduleAndRunRequestedTasks(VintageBuildTreeWorkController.kt:35)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$scheduleAndRunTasks$1(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$runBuild$4(DefaultBuildTreeLifecycleController.java:120)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$6(StateTransitionController.java:169)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$7(StateTransitionController.java:169)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:46)
	at org.gradle.internal.model.StateTransitionController.transition(StateTransitionController.java:169)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.runBuild(DefaultBuildTreeLifecycleController.java:117)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:77)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:72)
	at org.gradle.tooling.internal.provider.runner.BuildModelActionRunner.run(BuildModelActionRunner.java:53)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.internal.buildtree.ProblemReportingBuildActionRunner.run(ProblemReportingBuildActionRunner.java:49)
	at org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner.run(BuildOutcomeReportingBuildActionRunner.java:71)
	at org.gradle.tooling.internal.provider.FileSystemWatchingBuildActionRunner.run(FileSystemWatchingBuildActionRunner.java:135)
	at org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.run(BuildCompletionNotifyingBuildActionRunner.java:41)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.lambda$execute$0(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:130)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.execute(RootBuildLifecycleBuildActionExecutor.java:54)
	at org.gradle.internal.buildtree.InitDeprecationLoggingActionExecutor.execute(InitDeprecationLoggingActionExecutor.java:62)
	at org.gradle.internal.buildtree.InitProblems.execute(InitProblems.java:36)
	at org.gradle.internal.buildtree.DefaultBuildTreeContext.execute(DefaultBuildTreeContext.java:40)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.lambda$execute$0(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.internal.buildtree.BuildTreeState.run(BuildTreeState.java:60)
	at org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor.execute(BuildTreeLifecycleBuildActionExecutor.java:71)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:67)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor$2.call(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor.execute(RunAsBuildOperationBuildActionExecutor.java:63)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.lambda$execute$0(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor.execute(RunAsWorkerThreadBuildActionExecutor.java:36)
	at org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor.execute(ContinuousBuildActionExecutor.java:110)
	at org.gradle.tooling.internal.provider.SubscribableBuildActionExecutor.execute(SubscribableBuildActionExecutor.java:64)
	at org.gradle.internal.session.DefaultBuildSessionContext.execute(DefaultBuildSessionContext.java:46)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:92)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:80)
	at org.gradle.internal.session.BuildSessionState.run(BuildSessionState.java:73)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:62)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:41)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:64)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:32)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:51)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:39)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:47)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:31)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.lambda$execute$2(ForwardStdInToThisProcess.java:79)
	at org.gradle.internal.daemon.clientinput.ClientInputForwarder.forwardInput(ClientInputForwarder.java:80)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:66)
	at org.gradle.tooling.internal.provider.ForwardStdInToThisProcess.execute(ForwardStdInToThisProcess.java:39)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:43)
	at org.gradle.tooling.internal.provider.SystemPropertySetterExecuter.execute(SystemPropertySetterExecuter.java:27)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:42)
	at org.gradle.tooling.internal.provider.RunInProcess.execute(RunInProcess.java:32)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:44)
	at org.gradle.tooling.internal.provider.DaemonBuildActionExecuter.execute(DaemonBuildActionExecuter.java:30)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:59)
	at org.gradle.tooling.internal.provider.LoggingBridgingBuildActionExecuter.execute(LoggingBridgingBuildActionExecuter.java:38)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:273)
	at org.gradle.tooling.internal.provider.ProviderConnection.run(ProviderConnection.java:181)
	at org.gradle.tooling.internal.provider.DefaultConnection.getModel(DefaultConnection.java:154)
	at org.gradle.tooling.internal.consumer.connection.CancellableModelBuilderBackedModelProducer.produceModel(CancellableModelBuilderBackedModelProducer.java:53)
	at org.gradle.tooling.internal.consumer.connection.PluginClasspathInjectionSupportedCheckModelProducer.produceModel(PluginClasspathInjectionSupportedCheckModelProducer.java:38)
	at org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.run(AbstractConsumerConnection.java:64)
	at org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection.run(ParameterValidatingConsumerConnection.java:49)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:96)
	at org.gradle.tooling.internal.consumer.DefaultBuildLauncher$1.run(DefaultBuildLauncher.java:88)
	at org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor.run(LazyConsumerActionExecutor.java:143)
	at org.gradle.tooling.internal.consumer.connection.CancellableConsumerActionExecutor.run(CancellableConsumerActionExecutor.java:45)
	at org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor.run(ProgressLoggingConsumerActionExecutor.java:61)
	at org.gradle.tooling.internal.consumer.connection.RethrowingErrorsConsumerActionExecutor.run(RethrowingErrorsConsumerActionExecutor.java:38)
	at org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor$1$1.run(DefaultAsyncConsumerActionExecutor.java:66)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

> Configure project :api
Evaluating project ':api' using build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/build.gradle'.

> Configure project :api:api-jersey
Evaluating project ':api:api-jersey' using build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-jersey/build.gradle'.

> Configure project :api:api-objects
Evaluating project ':api:api-objects' using build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-objects/build.gradle'.

> Configure project :api:api-typescript
Evaluating project ':api:api-typescript' using build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-typescript/build.gradle'.

> Configure project :api:api-undertow
Evaluating project ':api:api-undertow' using build file '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-undertow/build.gradle'.
All projects evaluated.
Abbreviated task name 'Jar' matched 'jar'
Task name matched 'printServiceDependencies'
Selected primary task 'Jar' from project :
Selected primary task 'printServiceDependencies' from project :
Tasks to be executed: [task ':extractConjureJava', task ':extractConjure', task ':api:copyConjureSourcesIntoBuild', task ':api:compileIr', task ':api:compileConjureJersey', task ':api:compileConjureObjects', task ':api:api-objects:compileJava', task ':api:api-jersey:compileJava', task ':api:api-jersey:compileRecommendedProductDependencies', task ':api:api-jersey:processResources', task ':api:api-jersey:classes', task ':api:api-jersey:configureEndpointVersionBounds', task ':api:api-jersey:configureProductDependencies', task ':api:api-jersey:jar', task ':api:api-objects:processResources', task ':api:api-objects:classes', task ':api:api-objects:jar', task ':api:compileConjureUndertow', task ':api:api-undertow:compileJava', task ':api:api-undertow:processResources', task ':api:api-undertow:classes', task ':api:api-undertow:jar', task ':api:printServiceDependencies']
Tasks that were excluded: []
Resolve mutations for :extractConjureJava (Thread[Execution worker,5,main]) started.
:extractConjureJava (Thread[Execution worker,5,main]) started.

> Task :extractConjureJava
Custom actions are attached to task ':extractConjureJava'.
Caching disabled for task ':extractConjureJava' because:
  Build cache is disabled
  Caching has not been enabled for the task
Task ':extractConjureJava' is not up-to-date because:
  No history is available.
Invocation of Task.project at execution time has been deprecated. This will fail with an error in Gradle 10.0. This API is incompatible with the configuration cache, which will become the only mode supported by Gradle in a future release. Consult the upgrading guide for further information: https://docs.gradle.org/8.14.2/userguide/upgrading_version_7.html#task_project
	at org.gradle.internal.cc.impl.DeprecatedFeaturesListener.nagUserAbout(DeprecatedFeaturesListener.kt:98)
	at org.gradle.internal.cc.impl.DeprecatedFeaturesListener.onProjectAccess(DeprecatedFeaturesListener.kt:70)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:36)
	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:24)
	at org.gradle.internal.event.DefaultListenerManager$ListenerDetails.dispatch(DefaultListenerManager.java:587)
	at org.gradle.internal.event.DefaultListenerManager$ListenerDetails.dispatch(DefaultListenerManager.java:557)
	at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:84)
	at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:70)
	at org.gradle.internal.event.DefaultListenerManager$EventBroadcast$ListenerDispatch.dispatch(DefaultListenerManager.java:414)
	at org.gradle.internal.event.DefaultListenerManager$EventBroadcast$ListenerDispatch.dispatch(DefaultListenerManager.java:399)
	at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:44)
	at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:67)
	at org.gradle.internal.event.DefaultListenerManager$EventBroadcast$ListenerDispatch.dispatch(DefaultListenerManager.java:414)
	at org.gradle.internal.event.DefaultListenerManager$EventBroadcast$ListenerDispatch.dispatch(DefaultListenerManager.java:399)
	at org.gradle.internal.dispatch.ProxyDispatchAdapter$DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:92)
	at jdk.proxy4/jdk.proxy4.$Proxy114.onProjectAccess(Unknown Source)
	at org.gradle.internal.cc.impl.AbstractTaskProjectAccessChecker.notifyProjectAccess(TaskExecutionAccessCheckers.kt:47)
	at org.gradle.api.internal.AbstractTask.getProject(AbstractTask.java:239)
	at org.gradle.api.DefaultTask.getProject(DefaultTask.java:59)
	at com.palantir.gradle.conjure.ExtractExecutableTask.lambda$new$0(ExtractExecutableTask.java:56)
	at org.gradle.internal.UncheckedException.uncheckedCall(UncheckedException.java:103)
	at org.gradle.util.internal.DeferredUtil.unpackNestableDeferred(DeferredUtil.java:83)
	at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:89)
	at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection$UnresolvedItemsCollector.visitContents(DefaultConfigurableFileCollection.java:635)
	at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection.visitChildren(DefaultConfigurableFileCollection.java:448)
	at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:112)
	at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:361)
	at org.gradle.api.internal.file.FileCollectionBackedFileTree.visitContents(FileCollectionBackedFileTree.java:94)
	at org.gradle.api.internal.file.FileCollectionBackedFileTree.visitContentsAsFileTrees(FileCollectionBackedFileTree.java:74)
	at org.gradle.api.internal.file.FilteredFileTree.visitChildren(FilteredFileTree.java:67)
	at org.gradle.api.internal.file.CompositeFileCollection.getSourceCollections(CompositeFileCollection.java:106)
	at org.gradle.api.internal.file.CompositeFileTree.getSourceCollections(CompositeFileTree.java:54)
	at org.gradle.api.internal.file.CompositeFileTree.visit(CompositeFileTree.java:107)
	at org.gradle.api.internal.file.copy.CopySpecActionImpl.execute(CopySpecActionImpl.java:43)
	at org.gradle.api.internal.file.copy.CopySpecActionImpl.execute(CopySpecActionImpl.java:25)
	at org.gradle.api.internal.file.copy.DefaultCopySpec$DefaultCopySpecResolver.walk(DefaultCopySpec.java:887)
	at org.gradle.api.internal.file.copy.DefaultCopySpec$DefaultCopySpecResolver.walk(DefaultCopySpec.java:889)
	at org.gradle.api.internal.file.copy.DefaultCopySpec.walk(DefaultCopySpec.java:595)
	at org.gradle.api.internal.file.copy.DelegatingCopySpecInternal.walk(DelegatingCopySpecInternal.java:316)
	at org.gradle.api.internal.file.copy.CopySpecBackedCopyActionProcessingStream.process(CopySpecBackedCopyActionProcessingStream.java:42)
	at org.gradle.api.internal.file.copy.DuplicateHandlingCopyActionDecorator.lambda$execute$1(DuplicateHandlingCopyActionDecorator.java:50)
	at org.gradle.api.internal.file.copy.NormalizingCopyActionDecorator.lambda$execute$1(NormalizingCopyActionDecorator.java:64)
	at org.gradle.api.internal.file.copy.SyncCopyActionDecorator.lambda$execute$1(SyncCopyActionDecorator.java:72)
	at org.gradle.api.internal.file.copy.FileCopyAction.execute(FileCopyAction.java:38)
	at org.gradle.api.internal.file.copy.SyncCopyActionDecorator.execute(SyncCopyActionDecorator.java:72)
	at org.gradle.api.internal.file.copy.NormalizingCopyActionDecorator.execute(NormalizingCopyActionDecorator.java:63)
	at org.gradle.api.internal.file.copy.DuplicateHandlingCopyActionDecorator.execute(DuplicateHandlingCopyActionDecorator.java:50)
	at org.gradle.api.internal.file.copy.CopyActionExecuter.execute(CopyActionExecuter.java:48)
	at org.gradle.api.tasks.AbstractCopyTask.copy(AbstractCopyTask.java:161)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at org.gradle.internal.reflect.JavaMethod.invoke(JavaMethod.java:125)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.doExecute(StandardTaskAction.java:58)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.execute(StandardTaskAction.java:51)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.execute(StandardTaskAction.java:29)
	at org.gradle.api.internal.tasks.execution.TaskExecution$3.run(TaskExecution.java:244)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeAction(TaskExecution.java:229)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeActions(TaskExecution.java:212)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeWithPreviousOutputFiles(TaskExecution.java:195)
	at org.gradle.api.internal.tasks.execution.TaskExecution.execute(TaskExecution.java:162)
	at org.gradle.internal.execution.steps.ExecuteStep.executeInternal(ExecuteStep.java:105)
	at org.gradle.internal.execution.steps.ExecuteStep.access$000(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:59)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:56)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:56)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:42)
	at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:75)
	at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:50)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:28)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:67)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:37)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:61)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:26)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:69)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:46)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:40)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:29)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:189)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$1(BuildCacheStep.java:75)
	at org.gradle.internal.Either$Right.fold(Either.java:175)
	at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:62)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:73)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:48)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:46)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:35)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:75)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:53)
	at java.base/java.util.Optional.orElseGet(Optional.java:364)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:35)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:49)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:27)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:71)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:39)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:65)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:36)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:107)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:56)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:64)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:43)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.executeWithNonEmptySources(AbstractSkipEmptyWorkStep.java:125)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:61)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:36)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:75)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:41)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$execute$0(AssignMutableWorkspaceStep.java:35)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:289)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:31)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:22)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)
	at java.base/java.util.Optional.orElseGet(Optional.java:364)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:48)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:35)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:64)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:127)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:116)
	at org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter.execute(ProblemsTaskPathTrackingTaskExecuter.java:41)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Extracted into task ':extractConjureJava' property 'outputDirectory'
Resolve mutations for :extractConjure (Thread[Execution worker,5,main]) started.
:extractConjure (Thread[Execution worker,5,main]) started.

> Task :extractConjure
Custom actions are attached to task ':extractConjure'.
Caching disabled for task ':extractConjure' because:
  Build cache is disabled
  Caching has not been enabled for the task
Task ':extractConjure' is not up-to-date because:
  No history is available.
Extracted into task ':extractConjure' property 'outputDirectory'
Resolve mutations for :api:copyConjureSourcesIntoBuild (Thread[Execution worker,5,main]) started.
:api:copyConjureSourcesIntoBuild (Thread[Execution worker,5,main]) started.

> Task :api:copyConjureSourcesIntoBuild
Custom actions are attached to task ':api:copyConjureSourcesIntoBuild'.
Caching disabled for task ':api:copyConjureSourcesIntoBuild' because:
  Build cache is disabled
  Not worth caching
Task ':api:copyConjureSourcesIntoBuild' is not up-to-date because:
  No history is available.
Resolve mutations for :api:compileIr (Thread[Execution worker,5,main]) started.
:api:compileIr (Thread[Execution worker,5,main]) started.

> Task :api:compileIr
Caching disabled for task ':api:compileIr' because:
  Build cache is disabled
Task ':api:compileIr' is not up-to-date because:
  No history is available.
Running in-process java with args: [compile, /Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/build/conjure, /Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/build/conjure-ir/api.conjure.json, --extensions, {"recommended-product-dependencies":[{"product-group":"bar","product-name":"foo","maximum-version":"2.0.0","recommended-version":"2.0.0","optional":false}]}]
Resolve mutations for :api:compileConjureJersey (Thread[Execution worker,5,main]) started.
:api:compileConjureJersey (Thread[Execution worker,5,main]) started.

> Task :api:compileConjureJersey
Custom actions are attached to task ':api:compileConjureJersey'.
Caching disabled for task ':api:compileConjureJersey' because:
  Build cache is disabled
Task ':api:compileConjureJersey' is not up-to-date because:
  No history is available.
Running in-process java with args: [--jersey, --jetbrainsContractAnnotations]
Resolve mutations for :api:compileConjureObjects (Thread[Execution worker,5,main]) started.
:api:compileConjureObjects (Thread[Execution worker,5,main]) started.

> Task :api:compileConjureObjects
Custom actions are attached to task ':api:compileConjureObjects'.
Caching disabled for task ':api:compileConjureObjects' because:
  Build cache is disabled
Task ':api:compileConjureObjects' is not up-to-date because:
  No history is available.
Running in-process java with args: [--jetbrainsContractAnnotations, --objects]
Resolve mutations for :api:api-objects:compileJava (Thread[Execution worker,5,main]) started.
:api:api-objects:compileJava (Thread[Execution worker,5,main]) started.
This JVM does not support getting OS memory, so no OS memory status updates will be broadcast

> Task :api:api-objects:compileJava
Caching disabled for task ':api:api-objects:compileJava' because:
  Build cache is disabled
Task ':api:api-objects:compileJava' is not up-to-date because:
  No history is available.
The input changes require a full rebuild for incremental task ':api:api-objects:compileJava'.
Compilation mode: default, forking compiler
Full recompilation is required because no incremental change information is available. This is usually caused by clean builds or changing compiler arguments.
Compiling with toolchain '/Users/finlayw/.gradle/gradle-jdks/amazon-corretto-17.0.15.6.1'.
file or directory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-objects/src/main/java', not found
Starting process 'Gradle Worker Daemon 1'. Working directory: /Users/finlayw/.gradle/workers Command: /Users/finlayw/.gradle/gradle-jdks/amazon-corretto-17.0.15.6.1/bin/java --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED @/Users/finlayw/.gradle/.tmp/gradle-worker-classpath17594796838216287207txt -Xmx512m -Dfile.encoding=UTF-8 -Duser.country=GB -Duser.language=en -Duser.variant worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Worker Daemon 1'
Successfully started process 'Gradle Worker Daemon 1'
Started Gradle worker daemon (0.206 secs) with fork options DaemonForkOptions{executable=/Users/finlayw/.gradle/gradle-jdks/amazon-corretto-17.0.15.6.1/bin/java, minHeapSize=null, maxHeapSize=null, jvmArgs=[--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED], keepAliveMode=DAEMON}.
Compiling with JDK Java compiler API.
Class dependency analysis for incremental compilation took 0.005 secs.
Created classpath snapshot for incremental compilation in 0.012 secs.
work action resolve main (project :api:api-objects) (Thread[Execution worker,5,main]) started.
Resolve mutations for :api:api-jersey:compileJava (Thread[Execution worker,5,main]) started.
:api:api-jersey:compileJava (Thread[Execution worker,5,main]) started.

> Task :api:api-jersey:compileJava
Caching disabled for task ':api:api-jersey:compileJava' because:
  Build cache is disabled
Task ':api:api-jersey:compileJava' is not up-to-date because:
  No history is available.
The input changes require a full rebuild for incremental task ':api:api-jersey:compileJava'.
Compilation mode: default, forking compiler
Full recompilation is required because no incremental change information is available. This is usually caused by clean builds or changing compiler arguments.
Compiling with toolchain '/Users/finlayw/.gradle/gradle-jdks/amazon-corretto-17.0.15.6.1'.
file or directory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-jersey/src/main/java', not found
Compiling with JDK Java compiler API.
Class dependency analysis for incremental compilation took 0.001 secs.
Created classpath snapshot for incremental compilation in 0.001 secs.
Resolve mutations for :api:api-jersey:compileRecommendedProductDependencies (Thread[Execution worker,5,main]) started.
:api:api-jersey:compileRecommendedProductDependencies (Thread[Execution worker,5,main]) started.

> Task :api:api-jersey:compileRecommendedProductDependencies
--> EXECUTING MY TRANSFORMER on 1 dependencies!
Caching disabled for task ':api:api-jersey:compileRecommendedProductDependencies' because:
  Build cache is disabled
  Caching has not been enabled for the task
Task ':api:api-jersey:compileRecommendedProductDependencies' is not up-to-date because:
  No history is available.
Resolve mutations for :api:api-jersey:processResources (Thread[Execution worker,5,main]) started.
:api:api-jersey:processResources (Thread[Execution worker,5,main]) started.

> Task :api:api-jersey:processResources
Caching disabled for task ':api:api-jersey:processResources' because:
  Build cache is disabled
  Not worth caching
Task ':api:api-jersey:processResources' is not up-to-date because:
  No history is available.
file or directory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-jersey/src/main/resources', not found
Resolve mutations for :api:api-jersey:classes (Thread[Execution worker,5,main]) started.
:api:api-jersey:classes (Thread[Execution worker,5,main]) started.

> Task :api:api-jersey:classes
Skipping task ':api:api-jersey:classes' as it has no actions.
Resolve mutations for :api:api-jersey:configureEndpointVersionBounds (Thread[Execution worker,5,main]) started.
:api:api-jersey:configureEndpointVersionBounds (Thread[Execution worker,5,main]) started.

> Task :api:api-jersey:configureEndpointVersionBounds
Caching disabled for task ':api:api-jersey:configureEndpointVersionBounds' because:
  Build cache is disabled
Task ':api:api-jersey:configureEndpointVersionBounds' is not up-to-date because:
  Task has not declared any outputs despite executing actions.
Resolve mutations for :api:api-jersey:configureProductDependencies (Thread[Execution worker,5,main]) started.
:api:api-jersey:configureProductDependencies (Thread[Execution worker,5,main]) started.

> Task :api:api-jersey:configureProductDependencies
--> EXECUTING MY TRANSFORMER on 1 dependencies!
Caching disabled for task ':api:api-jersey:configureProductDependencies' because:
  Build cache is disabled
Task ':api:api-jersey:configureProductDependencies' is not up-to-date because:
  Task has not declared any outputs despite executing actions.
Resolve mutations for :api:api-jersey:jar (Thread[Execution worker,5,main]) started.
:api:api-jersey:jar (Thread[Execution worker,5,main]) started.

> Task :api:api-jersey:jar
Caching disabled for task ':api:api-jersey:jar' because:
  Build cache is disabled
  Not worth caching
Task ':api:api-jersey:jar' is not up-to-date because:
  No history is available.
Resolve mutations for :api:api-objects:processResources (Thread[Execution worker,5,main]) started.
:api:api-objects:processResources (Thread[Execution worker,5,main]) started.

> Task :api:api-objects:processResources NO-SOURCE
Skipping task ':api:api-objects:processResources' as it has no source files and no previous output files.
Resolve mutations for :api:api-objects:classes (Thread[Execution worker,5,main]) started.
:api:api-objects:classes (Thread[Execution worker,5,main]) started.

> Task :api:api-objects:classes
Skipping task ':api:api-objects:classes' as it has no actions.
Resolve mutations for :api:api-objects:jar (Thread[Execution worker,5,main]) started.
:api:api-objects:jar (Thread[Execution worker,5,main]) started.

> Task :api:api-objects:jar
Caching disabled for task ':api:api-objects:jar' because:
  Build cache is disabled
  Not worth caching
Task ':api:api-objects:jar' is not up-to-date because:
  No history is available.
file or directory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-objects/build/resources/main', not found
Resolve mutations for :api:compileConjureUndertow (Thread[Execution worker,5,main]) started.
:api:compileConjureUndertow (Thread[Execution worker,5,main]) started.

> Task :api:compileConjureUndertow
Custom actions are attached to task ':api:compileConjureUndertow'.
Caching disabled for task ':api:compileConjureUndertow' because:
  Build cache is disabled
Task ':api:compileConjureUndertow' is not up-to-date because:
  No history is available.
Running in-process java with args: [--jetbrainsContractAnnotations, --undertow]
Resolve mutations for :api:api-undertow:compileJava (Thread[Execution worker,5,main]) started.
:api:api-undertow:compileJava (Thread[Execution worker,5,main]) started.

> Task :api:api-undertow:compileJava
Caching disabled for task ':api:api-undertow:compileJava' because:
  Build cache is disabled
Task ':api:api-undertow:compileJava' is not up-to-date because:
  No history is available.
The input changes require a full rebuild for incremental task ':api:api-undertow:compileJava'.
Compilation mode: default, forking compiler
Full recompilation is required because no incremental change information is available. This is usually caused by clean builds or changing compiler arguments.
Compiling with toolchain '/Users/finlayw/.gradle/gradle-jdks/amazon-corretto-17.0.15.6.1'.
file or directory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-undertow/src/main/java', not found
Compiling with JDK Java compiler API.
Class dependency analysis for incremental compilation took 0.002 secs.
Created classpath snapshot for incremental compilation in 0.009 secs.
Resolve mutations for :api:api-undertow:processResources (Thread[Execution worker,5,main]) started.
:api:api-undertow:processResources (Thread[Execution worker,5,main]) started.

> Task :api:api-undertow:processResources NO-SOURCE
Skipping task ':api:api-undertow:processResources' as it has no source files and no previous output files.
Resolve mutations for :api:api-undertow:classes (Thread[Execution worker,5,main]) started.
:api:api-undertow:classes (Thread[Execution worker,5,main]) started.

> Task :api:api-undertow:classes
Skipping task ':api:api-undertow:classes' as it has no actions.
Resolve mutations for :api:api-undertow:jar (Thread[Execution worker,5,main]) started.
:api:api-undertow:jar (Thread[Execution worker,5,main]) started.

> Task :api:api-undertow:jar
Caching disabled for task ':api:api-undertow:jar' because:
  Build cache is disabled
  Not worth caching
Task ':api:api-undertow:jar' is not up-to-date because:
  No history is available.
file or directory '/Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/api/api-undertow/build/resources/main', not found
Resolve mutations for :api:printServiceDependencies (Thread[Execution worker,5,main]) started.
:api:printServiceDependencies (Thread[Execution worker,5,main]) started.

> Task :api:printServiceDependencies
Caching disabled for task ':api:printServiceDependencies' because:
  Build cache is disabled
Task ':api:printServiceDependencies' is not up-to-date because:
  Task has not declared any outputs despite executing actions.
Service dependencies: extension 'serviceDependencies' property 'productDependencies'

[Incubating] Problems report is available at: file:///Volumes/git/gradle-conjure/gradle-conjure/build/nebulatest/com.palantir.gradle.conjure.ConjureServiceDependencyTest/custom-productDependenciesTransformer/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 5s
18 actionable tasks: 18 executed

 */