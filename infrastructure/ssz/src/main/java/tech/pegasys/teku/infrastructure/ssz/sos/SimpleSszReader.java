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

import org.apache.tuweni.bytes.Bytes;

public class SimpleSszReader implements SszReader {

  private final Bytes bytes;
  protected int offset = 0;

  public SimpleSszReader(final Bytes bytes) {
    this.bytes = bytes;
  }

  @Override
  public int getAvailableBytes() {
    return bytes.size() - offset;
  }

  @Override
  public SszReader slice(final int size) {
    checkIfAvailable(size);
    SimpleSszReader ret = new SimpleSszReader(bytes.slice(offset, size));
    offset += size;
    return ret;
  }

  @Override
  public Bytes read(final int length) {
    checkIfAvailable(length);
    Bytes ret = bytes.slice(offset, length);
    offset += length;
    return ret;
  }

  private void checkIfAvailable(final int size) {
    if (getAvailableBytes() < size) {
      throw new SszDeserializeException("Invalid SSZ: trying to read more bytes than available");
    }
  }

  @Override
  public void close() {
    if (getAvailableBytes() > 0) {
      throw new SszDeserializeException("Invalid SSZ: unread bytes remain: " + getAvailableBytes());
    }
  }
}
