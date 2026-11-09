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

package tech.pegasys.teku.spec.cache;

import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;

public interface IndexedAttestationCache {
  IndexedAttestationCache NOOP = (att, supplier) -> supplier.get();

  static IndexedAttestationCache noop() {
    return NOOP;
  }

  static CapturingIndexedAttestationCache capturing() {
    return new CapturingIndexedAttestationCache();
  }

  IndexedAttestationLight computeIfAbsent(
      Attestation attestation, Supplier<IndexedAttestationLight> attestationProvider);
}
