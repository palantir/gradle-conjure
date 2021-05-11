/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.palantir.gradle.conjure.api.EndpointMinimumVersion;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableEndpointMinimumVersions.class)
@JsonDeserialize(as = ImmutableEndpointMinimumVersions.class)
public interface EndpointMinimumVersions {
    @JsonProperty("endpoint-minimum-versions")
    Set<EndpointMinimumVersion> minimumVersions();

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableEndpointMinimumVersions.Builder {}
}
