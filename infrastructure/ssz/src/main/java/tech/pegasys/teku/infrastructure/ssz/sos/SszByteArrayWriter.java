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

public class SszByteArrayWriter implements SszWriter {
  private final byte[] bytes;
  private int size = 0;

  public SszByteArrayWriter(final int maxSize) {
    bytes = new byte[maxSize];
  }

  @Override
  public void write(final byte[] bytes, final int offset, final int length) {
    System.arraycopy(bytes, offset, this.bytes, this.size, length);
    this.size += length;
  }

  public byte[] getBytesArray() {
    return bytes;
  }

  public int getLength() {
    return size;
  }

  public Bytes toBytes() {
    return Bytes.wrap(bytes, 0, size);
  }
}
