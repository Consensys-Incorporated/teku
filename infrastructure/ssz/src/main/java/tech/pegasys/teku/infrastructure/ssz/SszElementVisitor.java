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

package tech.pegasys.teku.infrastructure.ssz;

import org.apache.tuweni.bytes.Bytes;

/** Receives the serialized bytes of one element, see {@link SszList#forEachElementSsz}. */
@FunctionalInterface
public interface SszElementVisitor {
  /**
   * @param index the element index
   * @param data bytes holding the element, valid only during the call and not to be modified
   * @param offset offset of the element in {@code data}
   * @return {@code false} to stop the iteration
   */
  boolean visit(int index, Bytes data, int offset);
}
