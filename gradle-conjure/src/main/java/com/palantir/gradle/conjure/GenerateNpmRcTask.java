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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.serialization.ObjectMappers;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

public class GenerateNpmRcTask extends DefaultTask {
    private static final JsonMapper MAPPER = ObjectMappers.newClientJsonMapper();

    private static final String NPM_REGISTRY_URI_PROPERTY = "npmRegistryUri";
    private final RegularFileProperty outputFile = getProject().getObjects().fileProperty();
    private final Property<String> username = getProject().getObjects().property(String.class);
    private final Property<String> password = getProject().getObjects().property(String.class);
    private final Property<String> packageName = getProject().getObjects().property(String.class);

    @OutputFile
    public final RegularFileProperty getOutputFile() {
        return this.outputFile;
    }

    @Input
    public final Property<String> getUsername() {
        return username;
    }

    @Input
    public final Property<String> getPassword() {
        return password;
    }

    @Input
    public final Property<String> getPackageName() {
        return packageName;
    }

    // mainly for tests and should not be included in the generator options
    private String getNpmRegistryUri() {
        String userRegistry = getProject().hasProperty(NPM_REGISTRY_URI_PROPERTY)
                ? getProject().property(NPM_REGISTRY_URI_PROPERTY).toString()
                : "https://registry.npmjs.org";
        return userRegistry.endsWith("/") ? userRegistry.substring(0, userRegistry.length() - 1) : userRegistry;
    }

    @TaskAction
    public final void createNpmrc() throws IOException, InterruptedException {
        int slashIndex = packageName.get().indexOf("/");
        Optional<String> scope = packageName.get().startsWith("@") && slashIndex != -1
                ? Optional.of(packageName.get().substring(1, slashIndex))
                : Optional.empty();

        String registryUri = getNpmRegistryUri();
        String strippedUri = registryUri.startsWith("https://") ? registryUri.substring(8) : registryUri.substring(7);
        NpmTokenResponse npmTokenResponse = tokenFromCreds(registryUri, username.get(), password.get());

        String scopeRegistry = scope.map(s -> s + ":").orElse("");
        String npmRcContents = String.format(
                "%sregistry=%s\n//%s/:_authToken=%s",
                scopeRegistry, registryUri, strippedUri, npmTokenResponse.token());

        try {
            Files.writeString(outputFile.getAsFile().get().toPath(), npmRcContents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static NpmTokenResponse tokenFromCreds(String registryUri, String username, String password)
            throws IOException, InterruptedException {
        HttpResponse<NpmTokenResponse> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder()
                                .header(HttpHeaders.ACCEPT, "application/json")
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .uri(URI.create(String.format("%s/-/user/org.couchdb.user:%s", registryUri, username)))
                                .PUT(BodyPublishers.ofString(
                                        MAPPER.writeValueAsString(ImmutableNpmTokenRequest.of(username, password))))
                                .build(),
                        responseInfo -> BodySubscribers.mapping(BodySubscribers.ofByteArray(), inputStream -> {
                            try {
                                return MAPPER.readValue(inputStream, NpmTokenResponse.class);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Call to registry failed: " + response.statusCode());
        }

        return response.body();
    }

    @Immutable
    @JsonSerialize(as = ImmutableNpmTokenRequest.class)
    @JsonDeserialize(as = ImmutableNpmTokenRequest.class)
    interface NpmTokenRequest {
        @Parameter
        String name();

        @Parameter
        String password();
    }

    @Immutable
    @JsonSerialize(as = ImmutableNpmTokenResponse.class)
    @JsonDeserialize(as = ImmutableNpmTokenResponse.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    interface NpmTokenResponse {
        @Parameter
        String token();
    }
}
