package org.locationtech.geogig.porcelain.index;

import java.util.Objects;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.IndexDatabase;

import lombok.NonNull;

/**
 * A value object resulting of creating or updating an index, that provides access to the
 * {@link IndexInfo index information} and the {@link RevTree} the index points to.
 *
 */
public final class Index {

    private final IndexInfo indexInfo;

    private final ObjectId indexTree;

    private final IndexDatabase indexdb;

    /**
     * Construct a new {@link Index} with the given parameters.
     * 
     * @param indexInfo the {@link IndexInfo} of the index
     * @param indexTree the {@link ObjectId} of the indexed tree
     * @param indexdb the index database
     */
    public Index(@NonNull IndexInfo indexInfo, @NonNull ObjectId indexTree,
            @NonNull IndexDatabase indexdb) {
        this.indexInfo = indexInfo;
        this.indexTree = indexTree;
        this.indexdb = indexdb;
    }

    /**
     * @return the {@link IndexInfo} of the index
     */
    public IndexInfo info() {
        return indexInfo;
    }

    /**
     * @return the {@link ObjectId} of the indexed tree
     */
    public ObjectId indexTreeId() {
        return indexTree;
    }

    /**
     * @return the {@link RevTree} that represents the indexed tree
     */
    public RevTree indexTree() {
        return indexdb.getTree(indexTree);
    }

    public @Override boolean equals(Object o) {
        if (o instanceof Index) {
            Index i = (Index) o;
            return info().equals(i.info()) && indexTreeId().equals(i.indexTreeId());
        }
        return false;
    }

    public @Override int hashCode() {
        return Objects.hash(indexInfo, indexTree);
    }

    public @Override String toString() {
        return String.format("Index(%s) %s on %s(%s)", indexTree.toString().substring(0, 8),
                indexInfo.getIndexType(), indexInfo.getTreeName(), indexInfo.getAttributeName());
    }
}
