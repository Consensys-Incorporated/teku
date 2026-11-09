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

import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;

/**
 * Represents composite immutable ssz structure which has descendant ssz structures
 *
 * @param <SszChildT> the type of children {@link SszData}
 */
public interface SszComposite<SszChildT extends SszData> extends SszData {

  /** Returns number of children in this structure */
  default int size() {
    return (int) getSchema().getMaxLength();
  }

  /**
   * Returns the child at index
   *
   * @throws IndexOutOfBoundsException if index >= size()
   */
  SszChildT get(int index);

  @Override
  SszCompositeSchema<?> getSchema();

  @Override
  SszMutableComposite<SszChildT> createWritableCopy();
}
