/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.schema;

import static org.apache.phoenix.query.QueryConstants.ENCODED_CQ_COUNTER_INITIAL_VALUE;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.hbase.index.util.KeyValueBuilder;
import org.apache.phoenix.index.IndexMaintainer;
import org.apache.phoenix.jdbc.PhoenixConnection;


/**
 * Definition of a Phoenix table
 *
 *
 * @since 0.1
 */
public interface PTable extends PMetaDataEntity {
    public static final long INITIAL_SEQ_NUM = 0;
    public static final String IS_IMMUTABLE_ROWS_PROP_NAME = "IMMUTABLE_ROWS";
    public static final boolean DEFAULT_DISABLE_WAL = false;

    public enum ViewType {
        MAPPED((byte)1),
        READ_ONLY((byte)2),
        UPDATABLE((byte)3);

        private final byte[] byteValue;
        private final byte serializedValue;

        ViewType(byte serializedValue) {
            this.serializedValue = serializedValue;
            this.byteValue = Bytes.toBytes(this.name());
        }

        public byte[] getBytes() {
            return byteValue;
        }

        public boolean isReadOnly() {
            return this != UPDATABLE;
        }

        public byte getSerializedValue() {
            return this.serializedValue;
        }

        public static ViewType fromSerializedValue(byte serializedValue) {
            if (serializedValue < 1 || serializedValue > ViewType.values().length) {
                throw new IllegalArgumentException("Invalid ViewType " + serializedValue);
            }
            return ViewType.values()[serializedValue-1];
        }

        public ViewType combine(ViewType otherType) {
            if (otherType == null) {
                return this;
            }
            if (this == UPDATABLE && otherType == UPDATABLE) {
                return UPDATABLE;
            }
            return READ_ONLY;
        }
    }

    public enum IndexType {
        GLOBAL((byte)1),
        LOCAL((byte)2);

        private final byte[] byteValue;
        private final byte serializedValue;

        IndexType(byte serializedValue) {
            this.serializedValue = serializedValue;
            this.byteValue = Bytes.toBytes(this.name());
        }

        public byte[] getBytes() {
            return byteValue;
        }

        public byte getSerializedValue() {
            return this.serializedValue;
        }

        public static IndexType getDefault() {
            return GLOBAL;
        }

        public static IndexType fromToken(String token) {
            return IndexType.valueOf(token.trim().toUpperCase());
        }

        public static IndexType fromSerializedValue(byte serializedValue) {
            if (serializedValue < 1 || serializedValue > IndexType.values().length) {
                throw new IllegalArgumentException("Invalid IndexType " + serializedValue);
            }
            return IndexType.values()[serializedValue-1];
        }
    }

    public enum LinkType {
        /**
         * Link from a table to its index table
         */
        INDEX_TABLE((byte)1),
        /**
         * Link from a view to its physical table
         */
        PHYSICAL_TABLE((byte)2),
        /**
         * Link from a view to its parent table
         */
        PARENT_TABLE((byte)3);
        
        private final byte[] byteValue;
        private final byte serializedValue;

        LinkType(byte serializedValue) {
            this.serializedValue = serializedValue;
            this.byteValue = Bytes.toBytes(this.name());
        }

        public byte[] getBytes() {
            return byteValue;
        }

        public byte getSerializedValue() {
            return this.serializedValue;
        }

        public static LinkType fromSerializedValue(byte serializedValue) {
            if (serializedValue < 1 || serializedValue > LinkType.values().length) {
                return null;
            }
            return LinkType.values()[serializedValue-1];
        }
    }
    
    public enum StorageScheme {
        ONE_CELL_PER_KEYVALUE_COLUMN((byte)1),
        ONE_CELL_PER_COLUMN_FAMILY((byte)2);

        private final byte[] byteValue;
        private final byte serializedValue;
        
        StorageScheme(byte serializedValue) {
            this.serializedValue = serializedValue;
            this.byteValue = Bytes.toBytes(this.name());
        }

        public byte[] getBytes() {
            return byteValue;
        }

        public byte getSerializedMetadataValue() {
            return this.serializedValue;
        }

        public static StorageScheme fromSerializedValue(byte serializedValue) {
            if (serializedValue < 1 || serializedValue > StorageScheme.values().length) {
                return null;
            }
            return StorageScheme.values()[serializedValue-1];
        }
    }
    
    public static class QualifierEncodingScheme<E> implements QualifierEncoderDecoder<E> {
        
        public static final QualifierEncodingScheme NON_ENCODED_QUALIFIERS = new QualifierEncodingScheme<String>((byte)0, "NON_ENCODED_QUALIFIERS", null) {
            @Override
            public byte[] getEncodedBytes(String value) {
                return Bytes.toBytes(value);
            }

            @Override
            public String getDecodedValue(byte[] bytes) {
                return Bytes.toString(bytes);
            }

            @Override
            public String getDecodedValue(byte[] bytes, int offset, int length) {
                return Bytes.toString(bytes, offset, length);
            }
            
            @Override
            public boolean isEncodeable(String value) {
                return true;
            }
            
            @Override
            public String toString() {
                return "NON_ENCODED_QUALIFIERS";
            }
        };
        public static final QualifierEncodingScheme ONE_BYTE_QUALIFIERS = new QualifierEncodingScheme<Long>((byte)1, "ONE_BYTE_QUALIFIERS", 255l) {
            @Override
            public byte[] getEncodedBytes(Long value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long getDecodedValue(byte[] bytes) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long getDecodedValue(byte[] bytes, int offset, int length) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public boolean isEncodeable(Long value) {
                return true;
            }
            
            @Override
            public String toString() {
                return "ONE_BYTE_QUALIFIERS";
            }
        };
        public static final QualifierEncodingScheme TWO_BYTE_QUALIFIERS = new QualifierEncodingScheme<Long>((byte)2, "TWO_BYTE_QUALIFIERS", 65535l) {
            @Override
            public byte[] getEncodedBytes(Long value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long getDecodedValue(byte[] bytes) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long getDecodedValue(byte[] bytes, int offset, int length) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public boolean isEncodeable(Long value) {
                return true;
            }
            
            @Override
            public String toString() {
                return "TWO_BYTE_QUALIFIERS";
            }
        };
        public static final QualifierEncodingScheme THREE_BYTE_QUALIFIERS = new QualifierEncodingScheme<Long>((byte)3, "THREE_BYTE_QUALIFIERS", 16777215l) {
            @Override
            public byte[] getEncodedBytes(Long value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long getDecodedValue(byte[] bytes) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long getDecodedValue(byte[] bytes, int offset, int length) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public boolean isEncodeable(Long value) {
                return true;
            }
            
            @Override
            public String toString() {
                return "THREE_BYTE_QUALIFIERS";
            }
        };
        public static final QualifierEncodingScheme FOUR_BYTE_QUALIFIERS = new QualifierEncodingScheme<Long>((byte)4, "FOUR_BYTE_QUALIFIERS", 4294967295l) {
            @Override
            public byte[] getEncodedBytes(Long value) {
                return Bytes.toBytes(value);
            }

            @Override
            public Long getDecodedValue(byte[] bytes) {
                return Bytes.toLong(bytes);
            }

            @Override
            public Long getDecodedValue(byte[] bytes, int offset, int length) {
                return Bytes.toLong(bytes, offset, length);
            }
            
            @Override
            public boolean isEncodeable(Long value) {
                return true;
            }
            
            @Override
            public String toString() {
                return "FOUR_BYTE_QUALIFIERS";
            }
        };
        public static final QualifierEncodingScheme[] schemes = {NON_ENCODED_QUALIFIERS, ONE_BYTE_QUALIFIERS, TWO_BYTE_QUALIFIERS, THREE_BYTE_QUALIFIERS, FOUR_BYTE_QUALIFIERS}; 
        private final byte[] metadataBytes;
        private final byte metadataValue;
        private final Long maxQualifier;

        private QualifierEncodingScheme(byte serializedMetadataValue, String name, Long maxQualifier) {
            this.metadataValue = serializedMetadataValue;
            this.metadataBytes = Bytes.toBytes(name);
            this.maxQualifier = maxQualifier;
        }

        public byte[] getMetadataBytes() {
            return metadataBytes;
        }

        public byte getSerializedMetadataValue() {
            return this.metadataValue;
        }

        public static QualifierEncodingScheme fromSerializedValue(byte serializedValue) {
            if (serializedValue < 0 || serializedValue >= schemes.length) {
                return null;
            }
            return schemes[serializedValue];
        }
        
        public Long getMaxQualifier() {
            return maxQualifier;
        }

        @Override
        public byte[] getEncodedBytes(E value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public E getDecodedValue(byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public E getDecodedValue(byte[] bytes, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEncodeable(E value) {
            throw new UnsupportedOperationException();
        }
    }
    
    interface QualifierEncoderDecoder<E> {
        byte[] getEncodedBytes(E value);
        E getDecodedValue(byte[] bytes);
        E getDecodedValue(byte[] bytes, int offset, int length);
        boolean isEncodeable(E value);
    }

    long getTimeStamp();
    long getSequenceNumber();
    long getIndexDisableTimestamp();
    /**
     * @return table name
     */
    PName getName();
    PName getSchemaName();
    PName getTableName();
    PName getTenantId();

    /**
     * @return the table type
     */
    PTableType getType();

    PName getPKName();

    /**
     * Get the PK columns ordered by position.
     * @return a list of the PK columns
     */
    List<PColumn> getPKColumns();

    /**
     * Get all columns ordered by position.
     * @return a list of all columns
     */
    List<PColumn> getColumns();

    /**
     * @return A list of the column families of this table
     *  ordered by position.
     */
    List<PColumnFamily> getColumnFamilies();

    /**
     * Get the column family with the given name
     * @param family the column family name
     * @return the PColumnFamily with the given name
     * @throws ColumnFamilyNotFoundException if the column family cannot be found
     */
    PColumnFamily getColumnFamily(byte[] family) throws ColumnFamilyNotFoundException;

    PColumnFamily getColumnFamily(String family) throws ColumnFamilyNotFoundException;

    /**
     * Get the column with the given string name.
     * @param name the column name
     * @return the PColumn with the given name
     * @throws ColumnNotFoundException if no column with the given name
     * can be found
     * @throws AmbiguousColumnException if multiple columns are found with the given name
     */
    PColumn getPColumnForColumnName(String name) throws ColumnNotFoundException, AmbiguousColumnException;
    
    /**
     * Get the column with the given column qualifier.
     * @param column qualifier bytes
     * @return the PColumn with the given column qualifier
     * @throws ColumnNotFoundException if no column with the given column qualifier can be found
     * @throws AmbiguousColumnException if multiple columns are found with the given column qualifier
     */
    PColumn getPColumnForColumnQualifier(byte[] cf, byte[] cq) throws ColumnNotFoundException, AmbiguousColumnException; 
    
    /**
     * Get the PK column with the given name.
     * @param name the column name
     * @return the PColumn with the given name
     * @throws ColumnNotFoundException if no PK column with the given name
     * can be found
     * @throws ColumnNotFoundException
     */
    PColumn getPKColumn(String name) throws ColumnNotFoundException;

    /**
     * Creates a new row at the specified timestamp using the key
     * for the PK values (from {@link #newKey(ImmutableBytesWritable, byte[][])}
     * and the optional key values specified using values.
     * @param ts the timestamp that the key value will have when committed
     * @param key the row key of the key value
     * @param hasOnDupKey true if row has an ON DUPLICATE KEY clause and false otherwise.
     * @param values the optional key values
     * @return the new row. Use {@link org.apache.phoenix.schema.PRow#toRowMutations()} to
     * generate the Row to send to the HBase server.
     * @throws ConstraintViolationException if row data violates schema
     * constraint
     */
    PRow newRow(KeyValueBuilder builder, long ts, ImmutableBytesWritable key, boolean hasOnDupKey, byte[]... values);

    /**
     * Creates a new row for the PK values (from {@link #newKey(ImmutableBytesWritable, byte[][])}
     * and the optional key values specified using values. The timestamp of the key value
     * will be set by the HBase server.
     * @param key the row key of the key value
     * @param hasOnDupKey true if row has an ON DUPLICATE KEY clause and false otherwise.
     * @param values the optional key values
     * @return the new row. Use {@link org.apache.phoenix.schema.PRow#toRowMutations()} to
     * generate the row to send to the HBase server.
     * @throws ConstraintViolationException if row data violates schema
     * constraint
     */
    PRow newRow(KeyValueBuilder builder, ImmutableBytesWritable key, boolean hasOnDupKey, byte[]... values);

    /**
     * Formulates a row key using the values provided. The values must be in
     * the same order as {@link #getPKColumns()}.
     * @param key bytes pointer that will be filled in with the row key
     * @param values the PK column values
     * @return the number of values that were used from values to set
     * the row key
     */
    int newKey(ImmutableBytesWritable key, byte[][] values);

    RowKeySchema getRowKeySchema();

    /**
     * Return the number of buckets used by this table for salting. If the table does
     * not use salting, returns null.
     * @return number of buckets used by this table for salting, or null if salting is not used.
     */
    Integer getBucketNum();

    /**
     * Return the list of indexes defined on this table.
     * @return the list of indexes.
     */
    List<PTable> getIndexes();

    /**
     * For a table of index type, return the state of the table.
     * @return the state of the index.
     */
    PIndexState getIndexState();

    /**
     * @return the full name of the parent view for a view or data table for an index table 
     * or null if this is not a view or index table. Also returns null for a view of a data table 
     * (use @getPhysicalName for this case) 
     */
    PName getParentName();
    /**
     * @return the table name of the parent view for a view or data table for an index table 
     * or null if this is not a view or index table. Also returns null for a view of a data table 
     * (use @getPhysicalTableName for this case) 
     */
    PName getParentTableName();
    /**
     * @return the schema name of the parent view for a view or data table for an index table 
     * or null if this is not a view or index table. Also returns null for view of a data table 
     * (use @getPhysicalSchemaName for this case) 
     */
    PName getParentSchemaName();

    /**
     * For a view, return the name of table in Phoenix that physically stores data.
     * Currently a single name, but when views are allowed over multiple tables, will become multi-valued.
     * @return the name of the physical table storing the data.
     */
    public List<PName> getPhysicalNames();

    /**
     * For a view, return the name of table in HBase that physically stores data.
     * @return the name of the physical HBase table storing the data.
     */
    PName getPhysicalName();
    boolean isImmutableRows();

    boolean getIndexMaintainers(ImmutableBytesWritable ptr, PhoenixConnection connection);
    IndexMaintainer getIndexMaintainer(PTable dataTable, PhoenixConnection connection);
    PName getDefaultFamilyName();

    boolean isWALDisabled();
    boolean isMultiTenant();
    boolean getStoreNulls();
    boolean isTransactional();

    ViewType getViewType();
    String getViewStatement();
    Short getViewIndexId();
    PTableKey getKey();

    IndexType getIndexType();
    int getBaseColumnCount();

    /**
     * Determines whether or not we may optimize out an ORDER BY or do a GROUP BY
     * in-place when the optimizer tells us it's possible. This is due to PHOENIX-2067
     * and only applicable for tables using DESC primary key column(s) which have
     * not been upgraded.
     * @return true if optimizations row key order optimizations are possible
     */
    boolean rowKeyOrderOptimizable();
    
    /**
     * @return Position of the column with {@link PColumn#isRowTimestamp()} as true. 
     * -1 if there is no such column.
     */
    int getRowTimestampColPos();
    long getUpdateCacheFrequency();
    boolean isNamespaceMapped();
    
    /**
     * @return The sequence name used to get the unique identifier for views
     * that are automatically partitioned.
     */
    String getAutoPartitionSeqName();
    
    /**
     * @return true if the you can only add (and never delete) columns to the table,
     * you are also not allowed to delete the table  
     */
    boolean isAppendOnlySchema();
    StorageScheme getStorageScheme();
    QualifierEncodingScheme getEncodingScheme();
    EncodedCQCounter getEncodedCQCounter();
    
    /**
     * Class to help track encoded column qualifier counters per column family.
     */
    public class EncodedCQCounter {
        
        private final Map<String, Integer> familyCounters = new HashMap<>();
        
        /**
         * Copy constructor
         * @param counterToCopy
         * @return copy of the passed counter
         */
        public static EncodedCQCounter copy(EncodedCQCounter counterToCopy) {
            EncodedCQCounter cqCounter = new EncodedCQCounter();
            for (Entry<String, Integer> e : counterToCopy.values().entrySet()) {
                cqCounter.setValue(e.getKey(), e.getValue());
            }
            return cqCounter;
        }
        
        public static final EncodedCQCounter NULL_COUNTER = new EncodedCQCounter() {

            @Override
            public Integer getNextQualifier(String columnFamily) {
                return null;
            }

            @Override
            public void setValue(String columnFamily, Integer value) {
            }

            @Override
            public boolean increment(String columnFamily) {
                return false;
            }

            @Override
            public Map<String, Integer> values() {
                return Collections.emptyMap();
            }

        };
        
        /**
         * Get the next qualifier to be used for the column family.
         * This method also ends up initializing the counter if the
         * column family already doesn't have one.
         */
        @Nullable
        public Integer getNextQualifier(String columnFamily) {
            Integer counter = familyCounters.get(columnFamily);
            if (counter == null) {
                counter = ENCODED_CQ_COUNTER_INITIAL_VALUE;
                familyCounters.put(columnFamily, counter);
            }
            return counter;
        }
        
        public void setValue(String columnFamily, Integer value) {
            familyCounters.put(columnFamily, value);
        }
        
        /**
         * 
         * @param columnFamily
         * @return true if the counter was incremented, false otherwise.
         */
        public boolean increment(String columnFamily) {
            if (columnFamily == null) {
                return false;
            }
            Integer counter = familyCounters.get(columnFamily);
            if (counter == null) {
                counter = ENCODED_CQ_COUNTER_INITIAL_VALUE;
            }
            counter++;
            familyCounters.put(columnFamily, counter);
            return true;
        }
        
        public Map<String, Integer> values()  {
            return Collections.unmodifiableMap(familyCounters);
        }
        
    }
}
