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

import com.google.common.base.Suppliers;
import com.palantir.gradle.conjure.GradleVersion;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;

/**
 * Tools to allow operating on {@link Task tasks} lazily if possible (gradle 4.9+) while preserving
 * backwards-compatibility.
 */
public final class TaskUtils {
    private static final Pattern GRADLE_MAJOR_MINOR = Pattern.compile("(\\d+)\\.(\\d+)\\b.*");

    private Supplier<Optional<GradleVersion>> gradleVersion;

    public TaskUtils(Project project) {
        this.gradleVersion = Suppliers.memoize(() -> getGradleVersion(project));
    }

    private static Optional<GradleVersion> getGradleVersion(Project project) {
        Matcher matcher = GRADLE_MAJOR_MINOR.matcher(project.getGradle().getGradleVersion());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int major = Integer.parseUnsignedInt(matcher.group(1));
        int minor = Integer.parseUnsignedInt(matcher.group(2));
        return Optional.of(new GradleVersion(major, minor));
    }

    private boolean isAtLeast(int expectedMajor, int expectedMinor) {
        Optional<GradleVersion> versionOpt = this.gradleVersion.get();
        if (!versionOpt.isPresent()) {
            // Assume false
            return false;
        }
        GradleVersion version = versionOpt.get();
        return version.getMajor() > expectedMajor || (version.getMajor() == expectedMajor
                && version.getMinor() >= expectedMinor);
    }

    public <T extends Task> TaskProvider<T> registerTask(
            Project project, String taskName, Class<T> taskType, Action<? super T> configuration) {
        if (isAtLeast(4, 9)) {
            return new NewTaskProvider<>(project.getTasks().register(taskName, taskType, configuration));
        } else {
            return new OldTaskProvider<>(project.getTasks().create(taskName, taskType, configuration));
        }
    }

    public TaskProvider<Task> getNamedTask(Project project, String taskName) {
        if (isAtLeast(4, 9)) {
            return new NewTaskProvider<>(project.getTasks().named(taskName));
        } else {
            return new OldTaskProvider<>(project.getTasks().getByName(taskName));
        }
    }
}
