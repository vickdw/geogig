/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import static org.locationtech.geogig.model.Ref.TRANSACTIONS_PREFIX;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.storage.impl.AbstractRefDatabase;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import lombok.NonNull;

/**
 * Provides an implementation of a GeoGig ref database that utilizes the heap for the storage of
 * refs.
 */
public class HeapRefDatabase extends AbstractRefDatabase {

    private final ConcurrentMap<String, String> refs = new ConcurrentHashMap<>();

    public HeapRefDatabase() {
        this(false);
    }

    public HeapRefDatabase(boolean readOnly) {
        super(readOnly);
    }

    /**
     * @param name the name of the ref (e.g. {@code "refs/remotes/origin"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    public @Override String getRef(String name) {
        String val = refs.get(name);
        if (val == null) {
            return null;
        }
        try {
            ObjectId.valueOf(val);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        return val;
    }

    /**
     * @param name the name of the ref
     * @param value the value of the ref
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    public @Override void putRef(@NonNull String name, @NonNull String value) {
        ObjectId.valueOf(value);
        refs.put(name, value);
    }

    /**
     * @param refName the name of the ref to remove (e.g. {@code "HEAD"},
     *        {@code "refs/remotes/origin"}, etc).
     * @return the value of the ref before removing it, or {@code null} if it didn't exist
     */
    public @Override String remove(@NonNull String refName) {
        String oldValue = refs.remove(refName);
        if (oldValue != null && oldValue.startsWith("ref: ")) {
            oldValue = unmask(oldValue);
        }
        return oldValue;
    }

    /**
     * @param name the name of the symbolic ref (e.g. {@code "HEAD"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    public @Override String getSymRef(@NonNull String name) {
        String value = refs.get(name);
        if (value == null) {
            return null;
        }
        if (!value.startsWith("ref: ")) {
            throw new IllegalArgumentException(name + " is not a symbolic ref: '" + value + "'");
        }
        return unmask(value);
    }

    private String unmask(String value) {
        if (value.startsWith("ref: ")) {
            return value.substring("ref: ".length());
        }
        return value;
    }

    /**
     * @param name the name of the symbolic ref
     * @param val the value of the symbolic ref
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    public @Override void putSymRef(@NonNull String name, @NonNull String val) {
        val = "ref: " + val;
        refs.put(name, val);
    }

    private static class RefPrefixPredicate implements Predicate<String> {

        private final String prefix;

        RefPrefixPredicate(final String prefix) {
            this.prefix = prefix;
        }

        public @Override boolean apply(String refName) {
            return refName.startsWith(prefix);
        }
    }

    /**
     * @return all known references under the "refs" namespace (i.e. not top level ones like HEAD,
     *         etc), key'ed by ref name
     */
    public @Override Map<String, String> getAll() {

        Predicate<String> filter = Predicates.not(new RefPrefixPredicate(TRANSACTIONS_PREFIX));

        return getAll(filter);
    }

    public @Override Map<String, String> getAll(final String prefix) {
        Preconditions.checkNotNull(prefix, "namespace can't be null");
        Predicate<String> filter = new RefPrefixPredicate(prefix);
        return getAll(filter);
    }

    private Map<String, String> getAll(Predicate<String> keyFilter) {
        Map<String, String> all = new HashMap<>(Maps.filterKeys(this.refs, keyFilter));
        all = Maps.transformValues(all, this::unmask);
        return all;
    }

    public @Override Map<String, String> removeAll(final String namespace) {
        Preconditions.checkNotNull(namespace, "provided namespace is null");

        Predicate<String> keyPredicate = new Predicate<String>() {

            public @Override boolean apply(String refName) {
                return refName.startsWith(namespace);
            }
        };
        Map<String, String> removed = Maps.filterKeys(ImmutableMap.copyOf(this.refs), keyPredicate);
        for (String key : removed.keySet()) {
            refs.remove(key);
        }
        return removed;
    }

    public void putAll(Map<String, String> all) {
        try {
            lock();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
        try {
            all.forEach((name, value) -> {
                if (value.startsWith(Ref.REFS_PREFIX)) {
                    putSymRef(name, value);
                } else {
                    putRef(name, value);
                }
            });
        } finally {
            unlock();
        }
    }
}
