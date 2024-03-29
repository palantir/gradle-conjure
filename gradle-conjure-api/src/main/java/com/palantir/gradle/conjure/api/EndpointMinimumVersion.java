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

package com.palantir.gradle.conjure.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Objects;

@Deprecated
/**
 * @deprecated  As of release 5.13.0, replaced by {@link #EndpointVersionBound}
 */
public final class EndpointMinimumVersion implements Serializable {

    @JsonProperty("http-path")
    private String httpPath;

    @JsonProperty("http-method")
    private String httpMethod;

    @JsonProperty("min-version")
    private String minVersion;

    public String getHttpPath() {
        return httpPath;
    }

    public void setHttpPath(String httpPath) {
        this.httpPath = httpPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }

    @Override
    public boolean equals(Object source) {
        if (this == source) {
            return true;
        }
        if (source == null || getClass() != source.getClass()) {
            return false;
        }
        EndpointMinimumVersion that = (EndpointMinimumVersion) source;
        return Objects.equals(httpPath, that.httpPath)
                && Objects.equals(httpMethod, that.httpMethod)
                && Objects.equals(minVersion, that.minVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpPath, httpMethod, minVersion);
    }
}
