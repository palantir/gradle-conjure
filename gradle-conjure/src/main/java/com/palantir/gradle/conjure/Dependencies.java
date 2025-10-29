/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.conjure;

import com.palantir.gradle.conjure.api.GeneratorOptions;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;

final class Dependencies {

    // Make the old Java8 @Generated annotation available even when compiling with Java9+.
    static final String ANNOTATION_API_JAKARTA = "jakarta.annotation:jakarta.annotation-api:2.0.0";
    static final String ANNOTATION_API_JAVAX = "javax.annotation:javax.annotation-api:1.3.2";
    static final String JAXRS_API_JAKARTA = "jakarta.ws.rs:jakarta.ws.rs-api:3.0.0";
    static final String JAXRS_API_JAVAX = "javax.ws.rs:javax.ws.rs-api:2.1.1";
    static final String JAXRS_VALIDATION_API_JAKARTA = "jakarta.validation:jakarta.validation-api:3.0.0";
    static final String JAXRS_VALIDATION_API_JAVAX = "javax.validation:validation-api:2.0.1.Final";

    static final String CONJURE_JAVA_LIB = "com.palantir.conjure.java:conjure-lib:8.22.0";
    static final String CONJURE_UNDERTOW_LIB = "com.palantir.conjure.java:conjure-undertow-lib:8.22.0";
    static final String DIALOGUE_TARGET = "com.palantir.dialogue:dialogue-target:3.135.0";

    static final String GUAVA = "com.google.guava:guava:33.4.8-jre";
    static final String UNDERTOW_CORE = "io.undertow:undertow-core:2.3.18.Final";
    static final String JACKSON_ANNOTATIONS = "com.fasterxml.jackson.core:jackson-annotations:2.18.3";
    static final String JACKSON_DATABIND = "com.fasterxml.jackson.core:jackson-databind:2.18.3";
    static final String FINDBUGS = "com.google.code.findbugs:jsr305:3.0.2";
    static final String ERROR_PRONE_ANNOTATIONS = "com.google.errorprone:error_prone_annotations:2.38.0";
    static final String SAFE_LOGGING_PRECONDITIONS = "com.palantir.safe-logging:preconditions:3.9.0";
    static final String SAFE_LOGGING = "com.palantir.safe-logging:safe-logging:3.9.0";

    /**
     * Includes a version in order to ensure upgrades that opt into annotations
     * have a minimum version rather than failing builds.
     */
    static final String JETBRAINS_ANNOTATIONS = "org.jetbrains:annotations:23.0.0";

    static final Map<String, List<String>> CONJURE_OBJECTS_DEPENDENCIES = Map.of(
            "api", List.of(CONJURE_JAVA_LIB, JETBRAINS_ANNOTATIONS),
            "implementation",
                    List.of(
                            JACKSON_ANNOTATIONS,
                            JACKSON_DATABIND,
                            FINDBUGS,
                            ERROR_PRONE_ANNOTATIONS,
                            SAFE_LOGGING_PRECONDITIONS,
                            SAFE_LOGGING));

    private static final String JAKARTA_PACKAGES = "jakartaPackages";
    private static final String REQUIRE_NOT_NULL_AUTH_AND_BODY_PARAMS = "requireNotNullAuthAndBodyParams";
    private static final String OBJECTS = "objects";
    private static final String JERSEY = "jersey";
    private static final String DIALOGUE = "dialogue";
    private static final String UNDERTOW = "undertow";

    static boolean isJakartaPackages(GeneratorOptions options) {
        return isOption(options, JAKARTA_PACKAGES);
    }

    /**
     * See
     * https://github.com/palantir/conjure-java/blob/bd3dce573b5d92f6efd29f30a7c013f779030c91/conjure-java-core/src/main/java/com/palantir/conjure/java/Options.java#L37-L44.
     */
    static boolean isNotNullAuthAndBodyParams(GeneratorOptions options) {
        return isOption(options, REQUIRE_NOT_NULL_AUTH_AND_BODY_PARAMS);
    }

    static boolean isObjects(GeneratorOptions options) {
        return isOption(options, OBJECTS);
    }

    static boolean isJersey(GeneratorOptions options) {
        return isOption(options, JERSEY);
    }

    static boolean isDialogue(GeneratorOptions options) {
        return isOption(options, DIALOGUE);
    }

    static boolean isUndertow(GeneratorOptions options) {
        return isOption(options, UNDERTOW);
    }

    private static boolean isOption(GeneratorOptions options, String optionName) {
        return options.has(optionName) && Boolean.TRUE.equals(options.get(optionName));
    }

    static void setupObjectsProject(Project project) {
        project.getDependencies().add("api", CONJURE_JAVA_LIB);
        project.getDependencies().add("api", JETBRAINS_ANNOTATIONS);
        project.getDependencies().add("implementation", JACKSON_ANNOTATIONS);
        project.getDependencies().add("implementation", JACKSON_DATABIND);
        project.getDependencies().add("implementation", FINDBUGS);
        project.getDependencies().add("implementation", ERROR_PRONE_ANNOTATIONS);
        project.getDependencies().add("implementation", SAFE_LOGGING_PRECONDITIONS);
        project.getDependencies().add("implementation", SAFE_LOGGING);
    }

    static void setupDialogueProject(Project project) {
        project.getDependencies().add("api", DIALOGUE_TARGET);
        project.getDependencies().add("implementation", GUAVA);
        project.getDependencies().add("implementation", CONJURE_JAVA_LIB);
    }

    static void setupJerseyProject(Project project, GeneratorOptions options) {
        boolean useJakarta = Dependencies.isJakartaPackages(options);

        project.getDependencies()
                .add("api", useJakarta ? Dependencies.JAXRS_API_JAKARTA : Dependencies.JAXRS_API_JAVAX);
        project.getDependencies().add("implementation", CONJURE_JAVA_LIB);
        if (Dependencies.isNotNullAuthAndBodyParams(options)) {
            project.getDependencies()
                    .add(
                            "implementation",
                            useJakarta
                                    ? Dependencies.JAXRS_VALIDATION_API_JAKARTA
                                    : Dependencies.JAXRS_VALIDATION_API_JAVAX);
        }
        project.getDependencies()
                .add(
                        "compileOnly",
                        useJakarta ? Dependencies.ANNOTATION_API_JAKARTA : Dependencies.ANNOTATION_API_JAVAX);
    }

    static void setupUndertowProject(Project project) {
        project.getDependencies().add("api", Dependencies.CONJURE_UNDERTOW_LIB);
        project.getDependencies().add("implementation", Dependencies.UNDERTOW_CORE);
        project.getDependencies().add("implementation", Dependencies.GUAVA);
    }

    private Dependencies() {}
}
