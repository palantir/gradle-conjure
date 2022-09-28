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

final class Dependencies {

    /** Make the old Java8 @Generated annotation available even when compiling with Java9+. */
    static final String ANNOTATION_API = "jakarta.annotation:jakarta.annotation-api:1.3.5";

    static final String CONJURE_JAVA_LIB = "com.palantir.conjure.java:conjure-lib";
    static final String CONJURE_UNDERTOW_LIB = "com.palantir.conjure.java:conjure-undertow-lib";
    static final String DIALOGUE_TARGET = "com.palantir.dialogue:dialogue-target";
    static final String JAXRS_API = "jakarta.ws.rs:jakarta.ws.rs-api";
    /**
     * Includes a version in order to ensure upgrades that opt into annotations
     * have a minimum version rather than failing builds.
     */
    static final String JETBRAINS_ANNOTATIONS = "org.jetbrains:annotations:23.0.0";

    private Dependencies() {}
}
