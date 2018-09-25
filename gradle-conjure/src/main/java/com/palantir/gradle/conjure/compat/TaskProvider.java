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

import org.gradle.api.Action;
import org.gradle.api.Task;

/**
 * A potentially lazy provider for {@link Task}.
 * <p>
 * In gradle 4.9 and higher, this is implemented using {@link NewTaskProvider} which is a thin wrapper over
 * {@link org.gradle.api.tasks.TaskProvider}.
 * In older versions, this is implemented using {@link OldTaskProvider} which is a non-lazy wrapper around {@link Task}.
 */
public interface TaskProvider<T extends Task> {
    T get();

    /** Configure the task, lazily if possible. */
    void configure(Action<? super T> action);
}
