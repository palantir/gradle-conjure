/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;

public final class TestVersions {
    private TestVersions() {}

    public static final ImmutableList<String> VERSIONS = ImmutableList.of("7.6.4", "8.8");

    public static final String CONJURE = "4.48.0";
    public static final String CONJURE_JAVA = "8.22.0";
    public static final String CONJURE_POSTMAN = "0.1.2";
    public static final String CONJURE_PYTHON = "3.11.6";
    public static final String CONJURE_TYPESCRIPT = "3.8.1";
}
