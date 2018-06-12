/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.conjure;

import com.google.common.collect.ImmutableMap;
import groovy.lang.ReadOnlyPropertyException;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.gradle.api.plugins.ExtraPropertiesExtension;

public final class ConjureGeneratorParameters implements Serializable {
    private static final long serialVersionUID = 5676541916502995769L;
    private final Map<String, Object> storage = new LinkedHashMap<>();

    public void setProperty(String name, @Nullable Object newValue) {
        if (name.equals("properties")) {
            throw new ReadOnlyPropertyException("name", ExtraPropertiesExtension.class);
        } else {
            this.set(name, newValue);
        }
    }

    public Map<String, Object> getProperties() {
        return ImmutableMap.copyOf(this.storage);
    }

    public boolean has(String name) {
        return this.storage.containsKey(name);
    }

    @Nullable
    public Object get(String name) {
        Object value = this.find(name);
        if (value == null && !this.has(name)) {
            throw new RuntimeException("Unknown property: " + name);
        } else {
            return value;
        }
    }

    @Nullable
    public Object find(String name) {
        return this.storage.get(name);
    }

    private void set(String name, @Nullable Object value) {
        this.storage.put(name, value);
    }
}
