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

import com.google.common.io.ByteStreams
import com.palantir.gradle.dist.RecommendedProductDependencies
import com.palantir.gradle.dist.RecommendedProductDependenciesPlugin

import java.nio.charset.StandardCharsets
import java.util.jar.Manifest
import java.util.zip.ZipFile
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class ConjureJavaLocalCodegenPluginIntegrationSpec extends IntegrationSpec {
    def standardBuildFile = """
        buildscript {
            repositories {
                mavenCentral()
            }
        }
        
        allprojects {
            group = 'test.group'
            version = '1.0.0'
        
            repositories {
                mavenCentral()
            }

            configurations.all {
               resolutionStrategy {
                   force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure.java:conjure-lib:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'

                   force 'com.fasterxml.jackson.core:jackson-databind:2.10.2'
                   force 'com.fasterxml.jackson.core:jackson-annotations:2.10.2'
                   force 'com.palantir.safe-logging:safe-logging:1.12.0'
                   force 'com.palantir.safe-logging:preconditions:1.12.0'
               }
           }
        }
        
        apply plugin: 'com.palantir.conjure-java-local'
        dependencies {
            conjure 'com.palantir.conjure:conjure-api:${TestVersions.CONJURE}'
        }
    """.stripIndent()

    def setup() {
        buildFile << standardBuildFile;
    }

    def "generates projects"() {
        buildFile << """
        allprojects {
            group = 'test.group-with-dashes'
        }
        conjure {
            java {
                addFlag "jersey"
                addFlag "objects"
            }
        }
        """.stripIndent()
        addSubproject("conjure-api")

        when:
        def result = runTasksSuccessfully(":conjure-api:generateConjure")

        then:
        result.wasExecuted("extractConjureIr")
        result.wasExecuted("conjure-api:generateConjure")
        fileExists("build/conjure-ir/conjure-api.conjure.json")
        fileExists('conjure-api/src/generated/java/test/groupwithdashes/com/palantir/conjure/spec/ConjureDefinition.java')
        result.standardOutput.contains "with args: [--jersey, --jetbrainsContractAnnotations, --packagePrefix=test.groupwithdashes]"
        result.standardOutput.contains "with args: [--jetbrainsContractAnnotations, --objects, --packagePrefix=test.groupwithdashes]"
    }

    def "respects user provided packagePrefix"() {
        buildFile << """
        conjure {
            java {
                addFlag "objects"
                packagePrefix = "user.group"
            }
        }
        """.stripIndent()
        addSubproject("conjure-api")

        when:
        def result = runTasksSuccessfully(":conjure-api:generateConjure")

        then:
        result.wasExecuted("extractConjureIr")
        fileExists('conjure-api/src/generated/java/user/group/com/palantir/conjure/spec/ConjureDefinition.java')
        result.standardOutput.contains "with args: [--jetbrainsContractAnnotations, --objects, --packagePrefix=user.group]"
    }

    def 'check code compiles'() {
        addSubproject("conjure-api")
        buildFile << "conjure { java { addFlag 'objects' } }"

        when:
        ExecutionResult result = runTasksSuccessfully('check')

        then:
        result.wasExecuted(':conjure-api:compileJava')
        result.wasExecuted(':conjure-api:generateConjure')

        fileExists('conjure-api/src/generated/java/test/group/com/palantir/conjure/spec/ConjureDefinition.java')
    }

    def 'sets up idea source sets correctly'() {
        buildFile << """
        conjure { java { addFlag 'objects' } }
        subprojects {
            apply plugin: 'idea'
        }
        """.stripIndent()
        addSubproject("conjure-api")

        when:
        runTasksSuccessfully('idea')

        then:
        def slurper = new XmlParser()
        def module = slurper.parse(file('conjure-api/conjure-api.iml'))
        def sourcesFolderUrls = module.component.content.sourceFolder.@url

        sourcesFolderUrls.size() == 1
        sourcesFolderUrls.contains('file://$MODULE_DIR$/src/generated/java')
    }

    def 'embeds product dependencies correctly'() {
        addSubproject("conjure-api")
        buildFile << """
        conjure { java { addFlag 'objects' } }
        
        task modifyIr {
            doFirst {
                file('build/conjure-ir/conjure-api.conjure.json').text = '''
                {
                    "version": "1",
                    "extensions": {
                        "recommended-product-dependencies": [{
                            "product-group": "com.palantir.conjure",
                            "product-name": "conjure",
                            "minimum-version": "1.2.0",
                            "recommended-version": "1.2.0",
                            "maximum-version": "2.x.x"
                        }]
                    }
                }
                '''
            }
        }
        
        modifyIr.mustRunAfter extractConjureIr
        subprojects {
            tasks.jar.dependsOn modifyIr
        }
        """.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully('jar')

        then:
        result.wasExecuted(':conjure-api:compileJava')
        result.wasExecuted(':conjure-api:generateConjure')

        def expected = '{"recommended-product-dependencies":[{' +
                '"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure",' +
                '"minimum-version":"1.2.0",' +
                '"recommended-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"optional":false' +
                '}]}'
        def jarFile = file('conjure-api/build/libs/conjure-api-1.0.0.jar')
        readManifestRecommendedProductDeps(jarFile) == expected
        readResourceRecommendedProductDeps(jarFile) == expected
    }

    def "fails if missing corresponding subproject"() {
        when:
        buildFile << """
        task dummy {}
        """.stripIndent()
        def result = runTasksWithFailure("dummy")

        then:
        result.standardError.contains "Discovered dependencies [conjure-api] without corresponding subprojects."
    }

    def "fails if missing dependency"() {
        addSubproject("conjure-api")
        addSubproject("missing-api")
        when:
        buildFile << """
        task dummy {}
        """.stripIndent()
        def result = runTasksWithFailure("dummy")

        then:
        result.standardError.contains "Discovered subprojects [missing-api] without corresponding dependencies."
    }

    def "fails to generate without required flags"() {
        addSubproject("conjure-api")
        when:
        def result = runTasksWithFailure(":conjure-api:generateConjure")

        then:
        result.standardError.contains "Generator options must contain at least one of"
    }

    def readManifestRecommendedProductDeps(File jarFile) {
        def zf = new ZipFile(jarFile)
        def manifestEntry = zf.getEntry("META-INF/MANIFEST.MF")
        def manifest = new Manifest(zf.getInputStream(manifestEntry))
        return manifest.getMainAttributes().getValue(
                RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
    }

    def readResourceRecommendedProductDeps(File jarFile) {
        try (def zf = new ZipFile(jarFile)) {
            def manifestEntry = zf.getEntry(RecommendedProductDependenciesPlugin.RESOURCE_PATH)
            return new String(ByteStreams.toByteArray(zf.getInputStream(manifestEntry)), StandardCharsets.UTF_8)
        }
    }
}
