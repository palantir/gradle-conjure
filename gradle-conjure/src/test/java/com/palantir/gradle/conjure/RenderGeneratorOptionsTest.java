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

public class RenderGeneratorOptionsTest {
    private final GeneratorOptions generatorOptions = new GeneratorOptions();

    @Test
    public void testBoolean() {
        generatorOptions.setProperty("foo", true);
        generatorOptions.setProperty("bar", false);
        assertThat(RenderGeneratorOptions.toArgs(generatorOptions)).containsExactly("--foo", "--bar=false");
    }

    @Test
    public void testObjects() {
        generatorOptions.setProperty("foo", new Object() {
            @Override
            public String toString() {
                return "hel lo";
            }
        });
        assertThat(RenderGeneratorOptions.toArgs(generatorOptions)).containsExactly("--foo=hel lo");
    }

    @Test
    public void testCannotSetNull() {
        assertThatNullPointerException().isThrownBy(() -> generatorOptions.setProperty("foo", null));
    }

    @Test
    public void testNullToString() {
        generatorOptions.setProperty("foo", new Object() {
            @Override
            public String toString() {
                return null;
            }
        });
        assertThatNullPointerException().isThrownBy(() -> RenderGeneratorOptions.toArgs(generatorOptions));
    }
}
