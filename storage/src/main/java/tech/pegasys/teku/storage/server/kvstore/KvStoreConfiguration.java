/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.server.kvstore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.google.common.base.MoreObjects;
import java.nio.file.Path;
import org.rocksdb.CompressionType;

/**
 * Defines the configuration for a RocksDB database. The configuration used when a database is
 * created is written to a metadata.yaml file and reloaded to ensure we continue using compatible
 * values for the lifetime of that database.
 *
 * <p>To preserve backwards compatibility always ensure that the value assigned in field
 * declarations is compatible with existing databases. These values will be used if the field didn't
 * exist at the time the database was created, so typically should match the RocksDB default.
 *
 * <p>If the value to use for new databases, differs from the original, set it in a factory function
 * e.g. {@link #v5ArchiveDefaults()}.
 *
 * <p>Values that are safe to change for existing databases are marked with {@link
 * Access#WRITE_ONLY}. They will not be written to the metadata file but if present, the value will
 * be loaded providing a simple way to experiment with different values without it being fixed at
 * database creation.
 */
@SuppressWarnings("FieldCanBeFinal")
@JsonIgnoreProperties(ignoreUnknown = true)
public class KvStoreConfiguration {
  public static final int DEFAULT_MAX_OPEN_FILES = 1024;

  public static final int DEFAULT_LEVELDB_BLOCK_SIZE = 4096;

  public static final int DEFAULT_LEVELDB_WRITE_BUFFER_SIZE = 4_194_304;

  public static final int DEFAULT_LEVELDB_MAX_OPEN_FILES = 1000;

  public static final int DEFAULT_MAX_BACKGROUND_JOBS = 6;
  public static final int DEFAULT_BACKGROUND_THREAD_COUNT = 6;
  public static final long DEFAULT_CACHE_CAPACITY = 128 << 20; // 128MB
  public static final long DEFAULT_WRITE_BUFFER_CAPACITY = 128 << 20;
  private static final boolean DEFAULT_OPTIMISE_FOR_SMALL_DB = false;
  private static final boolean DEFAULT_BLOBDB_ENABLED = false;

  /** RocksDb number of log files to keep on disk */
  public static final long NUMBER_OF_LOG_FILES_TO_KEEP = 5;

  /** RocksDb Time to roll a log file (1 day = 3600 * 24 seconds) */
  public static final long TIME_TO_ROLL_LOG_FILE = 86_400L;

  /** Max total size of all WAL file, after which a flush is triggered */
  public static final long WAL_MAX_TOTAL_SIZE = 1_073_741_824L;

  /** Expected size of a single WAL file, to determine how many WAL files to keep around */
  public static final long EXPECTED_WAL_FILE_SIZE = 67_108_864L;

  public static final long ROCKSDB_BLOCK_SIZE = 32_768;

  /* --------------- Safe to Change Properties ------------ */

  @JsonProperty(value = "maxOpenFiles", access = Access.WRITE_ONLY)
  private int maxOpenFiles = DEFAULT_MAX_OPEN_FILES;

  @JsonProperty(value = "leveldbBlockSize", access = Access.WRITE_ONLY)
  private int leveldbBlockSize = DEFAULT_LEVELDB_BLOCK_SIZE;

  @JsonProperty(value = "leveldbMaxOpenFiles", access = Access.WRITE_ONLY)
  private int leveldbMaxOpenFiles = DEFAULT_LEVELDB_MAX_OPEN_FILES;

  @JsonProperty(value = "leveldbWriteBuffer", access = Access.WRITE_ONLY)
  private int leveldbWriteBufferSize = DEFAULT_LEVELDB_WRITE_BUFFER_SIZE;

  @JsonProperty(value = "maxBackgroundJobs", access = Access.WRITE_ONLY)
  private int maxBackgroundJobs = DEFAULT_MAX_BACKGROUND_JOBS;

  @JsonProperty(value = "backgroundThreadCount", access = Access.WRITE_ONLY)
  private int backgroundThreadCount = DEFAULT_BACKGROUND_THREAD_COUNT;

  @JsonProperty(value = "cacheCapacity", access = Access.WRITE_ONLY)
  private long cacheCapacity = DEFAULT_CACHE_CAPACITY;

  @JsonProperty(value = "writeBufferCapacity", access = Access.WRITE_ONLY)
  private long writeBufferCapacity = DEFAULT_WRITE_BUFFER_CAPACITY;

  // Safe to change but written to file as we need different defaults for hot and finalized
  @JsonProperty(value = "optimizeForSmallDb")
  private boolean optimizeForSmallDb = DEFAULT_OPTIMISE_FOR_SMALL_DB;

  @JsonProperty(value = "blobDbEnabled", access = Access.WRITE_ONLY)
  private boolean blobDbEnabled = DEFAULT_BLOBDB_ENABLED;

  /* ---------------     Fixed Properties     ------------ */

  @JsonProperty("compressionType")
  private CompressionType compressionType = CompressionType.LZ4_COMPRESSION;

  @JsonProperty("bottomMostCompressionType")
  private CompressionType bottomMostCompressionType = CompressionType.DISABLE_COMPRESSION_OPTION;

  @JsonIgnore private Path databaseDir;

  public static KvStoreConfiguration v4Settings(final Path databaseDir) {
    return new KvStoreConfiguration().withDatabaseDir(databaseDir);
  }

  public static KvStoreConfiguration v5HotDefaults() {
    final KvStoreConfiguration config = new KvStoreConfiguration();
    config.optimizeForSmallDb = true;
    return config;
  }

  public static KvStoreConfiguration v5ArchiveDefaults() {
    return new KvStoreConfiguration();
  }

  public static KvStoreConfiguration v6SingleDefaults() {
    return new KvStoreConfiguration();
  }

  public KvStoreConfiguration withDatabaseDir(final Path databaseDir) {
    this.databaseDir = databaseDir;
    return this;
  }

  public KvStoreConfiguration withBlobDbEnabled(final boolean blobDbEnabled) {
    this.blobDbEnabled = blobDbEnabled;
    return this;
  }

  public Path getDatabaseDir() {
    return databaseDir;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public int getLeveldbMaxOpenFiles() {
    return leveldbMaxOpenFiles;
  }

  public int getLeveldbBlockSize() {
    return leveldbBlockSize;
  }

  public int getLeveldbWriteBufferSize() {
    return leveldbWriteBufferSize;
  }

  public int getMaxBackgroundJobs() {
    return maxBackgroundJobs;
  }

  public int getBackgroundThreadCount() {
    return backgroundThreadCount;
  }

  public long getCacheCapacity() {
    return cacheCapacity;
  }

  public long getWriteBufferCapacity() {
    return writeBufferCapacity;
  }

  public CompressionType getCompressionType() {
    return compressionType;
  }

  public CompressionType getBottomMostCompressionType() {
    return bottomMostCompressionType;
  }

  public boolean optimizeForSmallDb() {
    return optimizeForSmallDb;
  }

  public boolean blobDbEnabled() {
    return blobDbEnabled;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("maxOpenFiles", maxOpenFiles)
        .add("maxBackgroundCompactions", maxBackgroundJobs)
        .add("backgroundThreadCount", backgroundThreadCount)
        .add("cacheCapacity", cacheCapacity)
        .add("writeBufferCapacity", writeBufferCapacity)
        .add("compressionType", compressionType)
        .add("bottomMostCompressionType", bottomMostCompressionType)
        .add("databaseDir", databaseDir)
        .add("blobDbEnabled", blobDbEnabled)
        .toString();
  }
}
