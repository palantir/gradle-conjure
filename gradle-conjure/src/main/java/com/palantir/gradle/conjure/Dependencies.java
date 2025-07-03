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
    /**
     * Includes a version in order to ensure upgrades that opt into annotations
     * have a minimum version rather than failing builds.
     */
    static final String JETBRAINS_ANNOTATIONS = "org.jetbrains:annotations:23.0.0";

    private static final String JAKARTA_PACKAGES = "jakartaPackages";
    private static final String REQUIRE_NOT_NULL_AUTH_AND_BODY_PARAMS = "requireNotNullAuthAndBodyParams";
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

    private Dependencies() {}
}
