/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.conjure;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConjureGeneratorParameters implements Serializable {
    private static final long serialVersionUID = 5676541916502995769L;
    private final Map<String, Object> storage = new LinkedHashMap<>();

    public void setProperty(String name, Object newValue) {
        if (name.equals("properties")) {
            throw new RuntimeException("Can't override the 'properties' property");
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

    public Object get(String name) {
        if (!this.has(name)) {
            throw new RuntimeException("Unknown property: " + name);
        } else {
            return this.storage.get(name);
        }
    }

    private void set(String name, Object value) {
        Preconditions.checkNotNull(value, "Value cannot be null (for property %s)", name);
        this.storage.put(name, value);
    }
}
