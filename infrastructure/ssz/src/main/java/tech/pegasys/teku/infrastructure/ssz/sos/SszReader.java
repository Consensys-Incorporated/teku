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

package tech.pegasys.teku.infrastructure.ssz.sos;

import java.io.Closeable;
import org.apache.tuweni.bytes.Bytes;

/** Simple reader interface for SSZ stream */
public interface SszReader extends Closeable {

  /** Creates an instance from {@link Bytes} */
  static SszReader fromBytes(final Bytes bytes) {
    return new SimpleSszReader(bytes);
  }

  /** Number of bytes available for reading */
  int getAvailableBytes();

  /**
   * Returns {@link SszReader} instance limited with {@code size} bytes Advances this reader current
   * read position to {@code size} bytes
   *
   * @throws SszDeserializeException If not enough bytes available for slice
   */
  SszReader slice(int size) throws SszDeserializeException;

  /**
   * Returns {@code length} bytes and advances this reader current read position to {@code length}
   * bytes
   *
   * @throws SszDeserializeException If not enough bytes available
   */
  Bytes read(int length) throws SszDeserializeException;

  /**
   * Closes this reader. Doesn't affect the 'parent' reader (if any) this instance was 'sliced' from
   *
   * @throws SszDeserializeException If unread bytes remain
   */
  @Override
  void close() throws SszDeserializeException;
}
