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

package com.palantir.gradle.conjure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.Test;

public class ConjureGeneratorParametersRendererTest {
    private final ConjureGeneratorParametersRenderer renderer = new ConjureGeneratorParametersRenderer();
    private final ConjureGeneratorParameters parameters = new ConjureGeneratorParameters();

    @Test
    public void testBoolean() {
        parameters.setProperty("foo", true);
        parameters.setProperty("bar", false);
        assertThat(renderer.toArgs(parameters)).containsExactly("--foo");
    }

    @Test
    public void testObjects() {
        parameters.setProperty("foo", new Object() {
            @Override
            public String toString() {
                return "hel lo";
            }
        });
        assertThat(renderer.toArgs(parameters)).containsExactly("--foo=hel lo");
    }

    @Test
    public void testCannotSetNull() {
        assertThatNullPointerException().isThrownBy(() -> parameters.setProperty("foo", null));
    }

    @Test
    public void testNullToString() {
        parameters.setProperty("foo", new Object() {
            @Override
            public String toString() {
                return null;
            }
        });
        assertThatNullPointerException().isThrownBy(() -> renderer.toArgs(parameters));
    }
}
