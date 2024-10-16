/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.DoNotLog;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

public class GenerateNpmrcTask extends DefaultTask {
    private static final JsonMapper MAPPER = ObjectMappers.newClientJsonMapper();

    private final RegularFileProperty outputFile = getProject().getObjects().fileProperty();
    private final Property<String> packageName = getProject().getObjects().property(String.class);
    private final Property<String> registryUri =
            getProject().getObjects().property(String.class).convention("https://registry.npmjs.org");
    private final Property<String> registryUsername = getProject().getObjects().property(String.class);
    private final Property<String> registryPassword = getProject().getObjects().property(String.class);
    private final Property<String> registryToken = getProject().getObjects().property(String.class);

    @OutputFile
    public final RegularFileProperty getOutputFile() {
        return this.outputFile;
    }

    @Input
    public final Property<String> getPackageName() {
        return packageName;
    }

    @Input
    public final Property<String> getRegistryUri() {
        return registryUri;
    }

    @Input
    @org.gradle.api.tasks.Optional
    public final Property<String> getUsername() {
        return registryUsername;
    }

    @Input
    @org.gradle.api.tasks.Optional
    public final Property<String> getPassword() {
        return registryPassword;
    }

    @Input
    @org.gradle.api.tasks.Optional
    public final Property<String> getToken() {
        return registryToken;
    }

    private String normalizedRegistryUri() {
        String uri = registryUri.get();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    @TaskAction
    public final void createNpmrc() throws InterruptedException {
        if (getToken().isPresent() && getPassword().isPresent()) {
            throw new GradleException("Either username and password or token must be specified but not both");
        }
        int slashIndex = packageName.get().indexOf("/");
        Optional<String> scope = packageName.get().startsWith("@") && slashIndex != -1
                ? Optional.of(packageName.get().substring(1, slashIndex))
                : Optional.empty();

        String normalizedUri = normalizedRegistryUri();
        String strippedUri =
                normalizedUri.startsWith("https://") ? normalizedUri.substring(8) : normalizedUri.substring(7);
        String username = registryUsername.getOrNull();
        String password = registryPassword.getOrNull();
        String token = registryToken.getOrNull();

        String tokenString;
        if (token != null) {
            tokenString = String.format("\n//%s/:_authToken=%s", strippedUri, token);
        } else if (username != null && password != null) {
            tokenString = String.format(
                    "\n//%s/:_authToken=%s",
                    strippedUri,
                    tokenFromCreds(normalizedUri, username, password).token());
        } else {
            tokenString = "";
        }

        String scopeRegistry = scope.map(s -> "@" + s + ":").orElse("");
        String npmRcContents = scopeRegistry + "registry=" + normalizedUri + "/" + tokenString;

        try {
            Path npmrcPath = outputFile.getAsFile().get().toPath().toAbsolutePath();
            Files.writeString(
                    npmrcPath,
                    npmRcContents,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            long size = Files.size(npmrcPath);
            getLogger().log(LogLevel.INFO, "Wrote npm config to '{}': {} bytes", npmrcPath, size);
        } catch (@DoNotLog IOException e) {
            throw new SafeRuntimeException(
                    "Error writing npmrc file",
                    SafeArg.of("registryUri", getRegistryUri()),
                    UnsafeArg.of("username", username));
        }
    }

    private static NpmTokenResponse tokenFromCreds(String registryUri, String username, String password)
            throws InterruptedException {
        try {
            HttpResponse<NpmTokenResponse> response = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder()
                                    .header(HttpHeaders.ACCEPT, "application/json")
                                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                    .uri(URI.create(
                                            String.format("%s/-/user/org.couchdb.user:%s", registryUri, username)))
                                    .PUT(BodyPublishers.ofString(serializeRequestBody(username, password)))
                                    .build(),
                            _responseInfo -> BodySubscribers.mapping(
                                    BodySubscribers.ofByteArray(), GenerateNpmrcTask::deserializeResponse));

            if (response.statusCode() >= 400) {
                throw new SafeRuntimeException(
                        "Call to registry failed",
                        SafeArg.of("registryUri", registryUri),
                        SafeArg.of("responseStatus", response.statusCode()),
                        UnsafeArg.of("username", username));
            }

            return response.body();
        } catch (IOException e) {
            throw new SafeRuntimeException(
                    "Failed to fetch token",
                    e,
                    SafeArg.of("registryUri", registryUri),
                    UnsafeArg.of("username", username));
        }
    }

    private static String serializeRequestBody(String username, String password) {
        try {
            return MAPPER.writeValueAsString(ImmutableNpmTokenRequest.of(username, password));
        } catch (@DoNotLog JsonProcessingException e) {
            throw new SafeRuntimeException("Error serializing NpmTokenRequest");
        }
    }

    private static NpmTokenResponse deserializeResponse(byte[] bytes) {
        try {
            return MAPPER.readValue(bytes, NpmTokenResponse.class);
        } catch (@DoNotLog IOException e) {
            throw new SafeRuntimeException(
                    "Failed to deserialize npm token response",
                    SafeArg.of("exceptionClass", e.getClass()),
                    SafeArg.of("bytes", bytes == null ? 0 : bytes.length));
        }
    }

    @DoNotLog
    @Immutable
    @JsonSerialize(as = ImmutableNpmTokenRequest.class)
    @JsonDeserialize(as = ImmutableNpmTokenRequest.class)
    interface NpmTokenRequest {
        @Parameter
        String name();

        @DoNotLog
        @Parameter
        String password();
    }

    @DoNotLog
    @Immutable
    @JsonSerialize(as = ImmutableNpmTokenResponse.class)
    @JsonDeserialize(as = ImmutableNpmTokenResponse.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    interface NpmTokenResponse {
        @DoNotLog
        @Parameter
        String token();
    }
}
