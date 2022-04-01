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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public final class EndpointVersionBound implements Serializable {

    @JsonProperty("http-path")
    private String httpPath;

    @JsonProperty("http-method")
    private String httpMethod;

    @JsonInclude(Include.NON_ABSENT)
    @JsonProperty("service-name")
    private String serviceName;

    @JsonInclude(Include.NON_ABSENT)
    @JsonProperty("endpoint-name")
    private String endpointName;

    @JsonInclude(Include.NON_ABSENT)
    @JsonProperty("deprecated")
    private Boolean deprecated;

    @JsonProperty("min-version")
    private String minVersion;

    @JsonInclude(Include.NON_ABSENT)
    @JsonProperty("max-version")
    private String maxVersion;

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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public Boolean getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }

    public Optional<String> getMaxVersion() {
        return Optional.ofNullable(maxVersion);
    }

    public void setMaxVersion(String maxVersion) {
        this.maxVersion = maxVersion;
    }

    @Override
    public boolean equals(Object source) {
        if (this == source) {
            return true;
        }
        if (source == null || getClass() != source.getClass()) {
            return false;
        }
        EndpointVersionBound that = (EndpointVersionBound) source;
        return Objects.equals(httpPath, that.httpPath)
                && Objects.equals(httpMethod, that.httpMethod)
                && Objects.equals(minVersion, that.minVersion)
                && Objects.equals(maxVersion, that.maxVersion)
                && Objects.equals(endpointName, that.endpointName)
                && Objects.equals(deprecated, that.deprecated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpPath, httpMethod, minVersion, maxVersion, endpointName, deprecated);
    }
}
