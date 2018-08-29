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

package com.palantir.gradle.conjure.compat;

import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;

/**
 * A wrapper around the {@link org.gradle.api.tasks.TaskProvider} introduced in gradle 4.9 that also implements
 * {@link TaskProvider} so that it can be used in a backwards-compatible way.
 */
public final class NewTaskProvider<T extends Task> implements TaskProvider<T>, org.gradle.api.tasks.TaskProvider<T> {
    private final org.gradle.api.tasks.TaskProvider<T> provider;

    public NewTaskProvider(org.gradle.api.tasks.TaskProvider provider) {
        this.provider = provider;
    }

    @Override
    public T get() {
        return provider.get();
    }

    @Override
    public void configure(Action<? super T> action) {
        provider.configure(action);
    }

    @Override
    public String getName() {
        return provider.getName();
    }

    @Override
    @Nullable
    public T getOrNull() {
        return provider.getOrNull();
    }

    @Override
    public T getOrElse(T def) {
        return provider.getOrElse(def);
    }

    @Override
    public <S> Provider<S> map(Transformer<? extends S, ? super T> transformer) {
        return provider.map(transformer);
    }

    @Override
    public boolean isPresent() {
        return provider.isPresent();
    }

}
