/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.phoenix.index.covered;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.phoenix.index.ValueGetter;
import org.apache.phoenix.index.covered.IndexCodec;
import org.apache.phoenix.index.covered.data.IndexMemStore;
import org.apache.phoenix.index.covered.data.LocalHBaseState;
import org.apache.phoenix.index.covered.update.ColumnReference;
import org.apache.phoenix.index.covered.update.ColumnTracker;
import org.apache.phoenix.index.covered.update.IndexedColumnGroup;
import org.apache.phoenix.index.scanner.ScannerBuilder;
import org.apache.phoenix.index.scanner.ScannerBuilder.CoveredDeleteScanner;
import org.apache.phoenix.index.util.IndexManagementUtil;

/**
 * Manage the state of the HRegion's view of the table, for the single row.
 * <p>
 * Currently, this is a single-use object - you need to create a new one for each row that you need to manage. In the
 * future, we could make this object reusable, but for the moment its easier to manage as a throw-away object.
 * <p>
 * This class is <b>not</b> thread-safe - it requires external synchronization is access concurrently.
 */
public class LocalTableState implements TableState {

    private long ts;
    private KeyValueStore memstore;
    private org.apache.phoenix.index.covered.data.LocalHBaseState table;
    private Mutation update;
    private Set<org.apache.phoenix.index.covered.update.ColumnTracker> trackedColumns = new HashSet<org.apache.phoenix.index.covered.update.ColumnTracker>();
    private ScannerBuilder scannerBuilder;
    private List<Cell> kvs = new ArrayList<Cell>();
    private List<? extends org.apache.phoenix.index.covered.update.IndexedColumnGroup> hints;
    private CoveredColumns columnSet;

    public LocalTableState(org.apache.phoenix.index.covered.data.LocalHBaseState table, Mutation update) {
        this.table = table;
        this.update = update;
        this.memstore = new org.apache.phoenix.index.covered.data.IndexMemStore();
        this.scannerBuilder = new ScannerBuilder(memstore, update);
        this.columnSet = new CoveredColumns();
    }

    public void addPendingUpdates(Cell... kvs) {
        if (kvs == null) return;
        addPendingUpdates(Arrays.asList(kvs));
    }

    public void addPendingUpdates(List<Cell> kvs) {
        if (kvs == null) return;
        setPendingUpdates(kvs);
        addUpdate(kvs);
    }

    private void addUpdate(List<Cell> list) {
        addUpdate(list, true);
    }

    private void addUpdate(List<Cell> list, boolean overwrite) {
        if (list == null) return;
        for (Cell kv : list) {
            this.memstore.add(kv, overwrite);
        }
    }

    private void addUpdateCells(List<Cell> list, boolean overwrite) {
        if (list == null) return;
        // Avoid a copy of the Cell into a KeyValue if it's already a KeyValue
        for (Cell c : list) {
            this.memstore.add(c, overwrite);
        }
    }

    @Override
    public long getCurrentTimestamp() {
        return this.ts;
    }

    /**
     * Set the current timestamp up to which the table should allow access to the underlying table.
     * This overrides the timestamp view provided by the indexer - use with care!
     * @param timestamp timestamp up to which the table should allow access.
     */
    public void setCurrentTimestamp(long timestamp) {
        this.ts = timestamp;
    }
    
    public void resetTrackedColumns() {
        this.trackedColumns.clear();
    }

    public Set<org.apache.phoenix.index.covered.update.ColumnTracker> getTrackedColumns() {
        return this.trackedColumns;
    }

    /**
     * Get a scanner on the columns that are needed by the index.
     * <p>
     * The returned scanner is already pre-seeked to the first {@link KeyValue} that matches the given
     * columns with a timestamp earlier than the timestamp to which the table is currently set (the
     * current state of the table for which we need to build an update).
     * <p>
     * If none of the passed columns matches any of the columns in the pending update (as determined
     * by {@link org.apache.phoenix.index.covered.update.ColumnReference#matchesFamily(byte[])} and
     * {@link org.apache.phoenix.index.covered.update.ColumnReference#matchesQualifier(byte[])}, then an empty scanner will be returned. This
     * is because it doesn't make sense to build index updates when there is no change in the table
     * state for any of the columns you are indexing.
     * <p>
     * <i>NOTE:</i> This method should <b>not</b> be used during
     * {@link IndexCodec#getIndexDeletes(TableState, BatchState, byte[], byte[])} as the pending update will not yet have been
     * applied - you are merely attempting to cleanup the current state and therefore do <i>not</i>
     * need to track the indexed columns.
     * <p>
     * As a side-effect, we update a timestamp for the next-most-recent timestamp for the columns you
     * request - you will never see a column with the timestamp we are tracking, but the next oldest
     * timestamp for that column.
     * @param indexedColumns the columns to that will be indexed
     * @param ignoreNewerMutations ignore mutations newer than m when determining current state. Useful
     *        when replaying mutation state for partial index rebuild where writes succeeded to the data
     *        table, but not to the index table.
     * @param indexMetaData TODO
     * @return an iterator over the columns and the {@link IndexUpdate} that should be passed back to
     *         the builder. Even if no update is necessary for the requested columns, you still need
     *         to return the {@link IndexUpdate}, just don't set the update for the
     *         {@link IndexUpdate}.
     * @throws IOException
     */
    public Pair<CoveredDeleteScanner, IndexUpdate> getIndexedColumnsTableState(
            Collection<? extends org.apache.phoenix.index.covered.update.ColumnReference> indexedColumns, boolean ignoreNewerMutations, boolean isStateForDeletes, IndexMetaData indexMetaData) throws IOException {
        // check to see if we haven't initialized any columns yet
        Collection<? extends org.apache.phoenix.index.covered.update.ColumnReference> toCover = this.columnSet.findNonCoveredColumns(indexedColumns);
        
        // add the covered columns to the set
        for (org.apache.phoenix.index.covered.update.ColumnReference ref : toCover) {
            this.columnSet.addColumn(ref);
        }
        boolean requiresPriorRowState = indexMetaData.requiresPriorRowState(update);
        if (!toCover.isEmpty()) {
            // no need to perform scan to find prior row values when the indexed columns are immutable, as
            // by definition, there won't be any. If we have indexed non row key columns, then we need to
            // look up the row so that we can formulate the delete of the index row correctly. We'll always
            // have our "empty" key value column, so we check if we have more than that as a basis for
            // needing to lookup the prior row values.
            if (requiresPriorRowState) {
                // add the current state of the row. Uses listCells() to avoid a new array creation.
                this.addUpdateCells(this.table.getCurrentRowState(update, toCover, ignoreNewerMutations).listCells(), false);
            }
        }

        // filter out things with a newer timestamp and track the column references to which it applies
        org.apache.phoenix.index.covered.update.ColumnTracker tracker = new org.apache.phoenix.index.covered.update.ColumnTracker(indexedColumns);
        synchronized (this.trackedColumns) {
            // we haven't seen this set of columns before, so we need to create a new tracker
            if (!this.trackedColumns.contains(tracker)) {
                this.trackedColumns.add(tracker);
            }
        }

        CoveredDeleteScanner scanner = this.scannerBuilder.buildIndexedColumnScanner(indexedColumns, tracker, ts,
                // If we're determining the index state for deletes and either
                // a) we've looked up the prior row state or
                // b) we're inserting immutable data
                // then allow a null scanner to be returned.
                // FIXME: this is crappy code - we need to simplify the global mutable secondary index implementation
                // TODO: use mutable transactional secondary index implementation instead (PhoenixTransactionalIndexer)
                isStateForDeletes && (requiresPriorRowState || insertingData(update)));
        return new Pair<CoveredDeleteScanner, IndexUpdate>(scanner, new IndexUpdate(tracker));
    }

 
    private static boolean insertingData(Mutation m) {
        for (Collection<Cell> cells : m.getFamilyCellMap().values()) {
            for (Cell cell : cells) {
                if (KeyValue.Type.codeToType(cell.getTypeByte()) != KeyValue.Type.Put) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public byte[] getCurrentRowKey() {
        return this.update.getRow();
    }

    /**
     * @param hints
     */
    public void setHints(List<? extends org.apache.phoenix.index.covered.update.IndexedColumnGroup> hints) {
        this.hints = hints;
    }

    @Override
    public List<? extends org.apache.phoenix.index.covered.update.IndexedColumnGroup> getIndexColumnHints() {
        return this.hints;
    }

    @Override
    public Collection<Cell> getPendingUpdate() {
        return this.kvs;
    }

    /**
     * Set the {@link KeyValue}s in the update for which we are currently building an index update, but don't actually
     * apply them.
     * 
     * @param update
     *            pending {@link KeyValue}s
     */
    public void setPendingUpdates(Collection<Cell> update) {
        this.kvs.clear();
        this.kvs.addAll(update);
    }
    
    /**
     * Apply the {@link KeyValue}s set in {@link #setPendingUpdates(Collection)}.
     */
    public void applyPendingUpdates() {
        this.addUpdate(kvs);
    }

    /**
     * Rollback all the given values from the underlying state.
     * 
     * @param values
     */
    public void rollback(Collection<KeyValue> values) {
        for (KeyValue kv : values) {
            this.memstore.rollback(kv);
        }
    }

    @Override
    public Pair<ValueGetter, IndexUpdate> getIndexUpdateState(Collection<? extends org.apache.phoenix.index.covered.update.ColumnReference> indexedColumns, boolean ignoreNewerMutations, boolean isStateForDeletes, IndexMetaData indexMetaData)
            throws IOException {
        Pair<CoveredDeleteScanner, IndexUpdate> pair = getIndexedColumnsTableState(indexedColumns, ignoreNewerMutations, isStateForDeletes, indexMetaData);
        ValueGetter valueGetter = IndexManagementUtil.createGetterFromScanner(pair.getFirst(), getCurrentRowKey());
        return new Pair<ValueGetter, IndexUpdate>(valueGetter, pair.getSecond());
    }
}