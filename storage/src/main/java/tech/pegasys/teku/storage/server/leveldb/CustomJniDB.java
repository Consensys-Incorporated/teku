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

import org.fusesource.leveldbjni.internal.JniDB;
import org.fusesource.leveldbjni.internal.NativeCache;
import org.fusesource.leveldbjni.internal.NativeComparator;
import org.fusesource.leveldbjni.internal.NativeDB;
import org.fusesource.leveldbjni.internal.NativeLogger;
import org.fusesource.leveldbjni.internal.NativeReadOptions;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.ReadOptions;

/** This class extends the JniDB class to provide a custom DBIterator. */
public class CustomJniDB extends JniDB {
  private final NativeDB db;

  public CustomJniDB(
      final NativeDB db,
      final NativeCache cache,
      final NativeComparator comparator,
      final NativeLogger logger) {
    super(db, cache, comparator, logger);
    this.db = db;
  }

  @Override
  public DBIterator iterator(final ReadOptions options) {
    if (this.db == null) {
      throw new DBException("Closed");
    } else {
      return new CustomJniDBIterator(this.db.iterator(this.convert(options)));
    }
  }

  // this method is private in the super class, so has been copied here
  private NativeReadOptions convert(final ReadOptions options) {
    if (options == null) {
      return null;
    } else {
      NativeReadOptions rc = new NativeReadOptions();
      rc.fillCache(options.fillCache());
      rc.verifyChecksums(options.verifyChecksums());
      if (options.snapshot() != null) {
        throw new UnsupportedOperationException("Snapshots are not supported");
      }

      return rc;
    }
  }
}
