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

import java.util.concurrent.Callable;
import org.gradle.api.Action;
import org.gradle.api.Task;

/**
 * This implements {@link Callable} os we can pass it to {@link Task#dependsOn}.
 */
public final class OldTaskProvider<T extends Task> implements TaskProvider<T>, Callable<T> {
    private final T task;

    public OldTaskProvider(T task) {
        this.task = task;
    }

    @Override
    public T get() {
        return task;
    }

    @Override
    public void configure(Action<? super T> action) {
        action.execute(task);
    }

    @Override
    public T call() {
        return get();
    }
}
