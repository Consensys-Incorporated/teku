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

import java.util.AbstractMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.fusesource.leveldbjni.internal.NativeDB;
import org.fusesource.leveldbjni.internal.NativeIterator;

/**
 * This is a copy of <a
 * href="https://github.com/fusesource/leveldbjni/blob/c810afcfa55a208f077ff4101cb318c0cc3e1bfb/leveldbjni/src/main/java/org/fusesource/leveldbjni/internal/JniDBIterator.java">
 * JniDBIterator</a> which also implements the methods from {@link CustomDBIterator}
 */
public class CustomJniDBIterator implements CustomDBIterator {
  private final NativeIterator iterator;

  CustomJniDBIterator(final NativeIterator iterator) {
    this.iterator = iterator;
  }

  @Override
  public void close() {
    this.iterator.delete();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seek(final byte[] key) {
    try {
      this.iterator.seek(key);
    } catch (final NativeDB.DBException e) {
      if (e.isNotFound()) {
        throw new NoSuchElementException();
      } else {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void seekToFirst() {
    this.iterator.seekToFirst();
  }

  @Override
  public void seekToLast() {
    this.iterator.seekToLast();
  }

  @Override
  public Map.Entry<byte[], byte[]> peekNext() {
    if (!this.iterator.isValid()) {
      throw new NoSuchElementException();
    } else {
      try {
        return new AbstractMap.SimpleImmutableEntry<>(this.iterator.key(), this.iterator.value());
      } catch (NativeDB.DBException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public boolean hasNext() {
    return this.iterator.isValid();
  }

  @Override
  public Map.Entry<byte[], byte[]> next() {
    final Map.Entry<byte[], byte[]> entry = this.peekNext();

    try {
      this.iterator.next();
      return entry;
    } catch (NativeDB.DBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean hasPrev() {
    if (!this.iterator.isValid()) {
      return false;
    } else {
      try {
        this.iterator.prev();

        final boolean ret;
        try {
          ret = this.iterator.isValid();
        } finally {
          if (this.iterator.isValid()) {
            this.iterator.next();
          } else {
            this.iterator.seekToFirst();
          }
        }

        return ret;
      } catch (NativeDB.DBException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public Map.Entry<byte[], byte[]> peekPrev() {
    try {
      this.iterator.prev();

      final Map.Entry<byte[], byte[]> entry;
      try {
        entry = this.peekNext();
      } finally {
        if (this.iterator.isValid()) {
          this.iterator.next();
        } else {
          this.iterator.seekToFirst();
        }
      }

      return entry;
    } catch (final NativeDB.DBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map.Entry<byte[], byte[]> prev() {
    final Map.Entry<byte[], byte[]> rc = this.peekPrev();

    try {
      this.iterator.prev();
      return rc;
    } catch (NativeDB.DBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public byte[] peekNextKey() {
    if (!this.iterator.isValid()) {
      throw new NoSuchElementException();
    } else {
      try {
        return this.iterator.key();
      } catch (NativeDB.DBException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public byte[] nextKey() {
    final byte[] key = this.peekNextKey();

    try {
      this.iterator.next();
      return key;
    } catch (NativeDB.DBException e) {
      throw new RuntimeException(e);
    }
  }
}
