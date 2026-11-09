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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import org.apache.tuweni.bytes.Bytes;

class BytesSerializer<T extends Bytes> implements KvStoreSerializer<T> {

  private final BytesFactory<T> bytesFactory;

  public BytesSerializer(final BytesFactory<T> bytesFactory) {
    this.bytesFactory = bytesFactory;
  }

  @Override
  public T deserialize(final byte[] data) {
    return bytesFactory.create(data);
  }

  @Override
  public byte[] serialize(final T value) {
    return value.toArrayUnsafe();
  }

  interface BytesFactory<T> {
    T create(byte[] bytes);
  }
}
