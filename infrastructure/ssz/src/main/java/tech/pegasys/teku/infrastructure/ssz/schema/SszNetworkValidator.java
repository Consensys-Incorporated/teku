/*
 * Copyright Consensys Software Inc., 2026
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

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;

/**
 * Validation applied by the network decoders (gossip and RPC) to a value right after it has been
 * SSZ deserialized. Used to enforce constraints that the SSZ type itself no longer expresses, such
 * as the soft length limits of progressive lists.
 *
 * <p>Only schemas that are decoded directly from the network declare a validator. Nested schemas
 * must not, since nothing consults them: the decoders ask the schema they decode with and nothing
 * else.
 */
@FunctionalInterface
public interface SszNetworkValidator<T extends SszData> {

  /**
   * @throws SszDeserializeException when the value violates a network-level constraint
   */
  void validate(T value);
}
