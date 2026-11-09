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

package tech.pegasys.teku.storage.server.leveldb;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.fusesource.leveldbjni.JniDBFactory;
import org.fusesource.leveldbjni.internal.NativeCache;
import org.fusesource.leveldbjni.internal.NativeComparator;
import org.fusesource.leveldbjni.internal.NativeCompressionType;
import org.fusesource.leveldbjni.internal.NativeDB;
import org.fusesource.leveldbjni.internal.NativeLogger;
import org.fusesource.leveldbjni.internal.NativeOptions;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBComparator;
import org.iq80.leveldb.Logger;
import org.iq80.leveldb.Options;

/**
 * This class extends the JniDBFactory class to provide a custom DBIterator. Only {@link
 * CustomJniDBFactory#open} has been changed from the original class
 */
@SuppressWarnings("EmptyCatch")
public class CustomJniDBFactory extends JniDBFactory {
  public static final CustomJniDBFactory FACTORY = new CustomJniDBFactory();
  public static final String VERSION;

  @Override
  public DB open(final File path, final Options options) throws IOException {
    final OptionsResourceHolder holder = new OptionsResourceHolder();

    NativeDB db = null;
    try {
      holder.init(options);
      db = NativeDB.open(holder.options, path);
    } finally {
      if (db == null) {
        holder.close();
      }
    }

    // this is the only line that has been functionally changed from the original class
    return new CustomJniDB(db, holder.cache, holder.comparator, holder.logger);
  }

  static {
    NativeDB.LIBRARY.load();
    String v = "unknown";

    try (final InputStream is = JniDBFactory.class.getResourceAsStream("version.txt")) {
      if (is != null) {
        try (final BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
          v = reader.readLine();
        }
      }
    } catch (final Throwable ignored) {
    }

    VERSION = v;
  }

  private static class OptionsResourceHolder {
    NativeCache cache;
    NativeComparator comparator;
    NativeLogger logger;
    NativeOptions options;

    private OptionsResourceHolder() {
      this.cache = null;
      this.comparator = null;
      this.logger = null;
    }

    private void init(final Options value) {
      this.options = new NativeOptions();
      this.options.blockRestartInterval(value.blockRestartInterval());
      this.options.blockSize((long) value.blockSize());
      this.options.createIfMissing(value.createIfMissing());
      this.options.errorIfExists(value.errorIfExists());
      this.options.maxOpenFiles(value.maxOpenFiles());
      this.options.paranoidChecks(value.paranoidChecks());
      this.options.writeBufferSize((long) value.writeBufferSize());
      switch (value.compressionType()) {
        case NONE -> this.options.compression(NativeCompressionType.kNoCompression);
        case SNAPPY -> this.options.compression(NativeCompressionType.kSnappyCompression);
      }

      if (value.cacheSize() > 0L) {
        this.cache = new NativeCache(value.cacheSize());
        this.options.cache(this.cache);
      }

      final DBComparator userComparator = value.comparator();
      if (userComparator != null) {
        this.comparator =
            new NativeComparator() {
              @Override
              public int compare(final byte[] key1, final byte[] key2) {
                return userComparator.compare(key1, key2);
              }

              @Override
              public String name() {
                return userComparator.name();
              }
            };
        this.options.comparator(this.comparator);
      }

      final Logger userLogger = value.logger();
      if (userLogger != null) {
        this.logger =
            new NativeLogger() {
              @Override
              public void log(final String message) {
                userLogger.log(message);
              }
            };
        this.options.infoLog(this.logger);
      }
    }

    private void close() {
      if (this.cache != null) {
        this.cache.delete();
      }

      if (this.comparator != null) {
        this.comparator.delete();
      }

      if (this.logger != null) {
        this.logger.delete();
      }
    }
  }
}
