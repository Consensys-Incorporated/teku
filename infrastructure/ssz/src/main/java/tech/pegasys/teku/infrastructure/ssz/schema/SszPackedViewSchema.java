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

package tech.pegasys.teku.infrastructure.ssz.schema;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;

/**
 * Implemented by the schema of a fixed-size container whose views can read their fields straight
 * from the container's serialized bytes, without a backing tree node of their own.
 *
 * <p>A collection of such elements packed in supernodes creates the view of an element from the
 * content of its supernode on every access and does not cache it: the view is one small object, so
 * creating it again is cheaper than keeping one per element alive.
 *
 * @param <V> the container view type
 */
public interface SszPackedViewSchema<V extends SszData> {

  /**
   * A view of the container serialized at {@code offset} in {@code packed}. The view reads its
   * fields from those bytes and creates its backing tree node only when it is asked for it, to be
   * hashed, serialized or stored in another structure.
   */
  V createFromPackedBytes(Bytes packed, int offset);
}
