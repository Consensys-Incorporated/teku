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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;

public class CapturingIndexedAttestationCache implements IndexedAttestationCache {
  private final Map<Attestation, IndexedAttestationLight> indexedAttestations = new HashMap<>();

  CapturingIndexedAttestationCache() {}

  @Override
  public IndexedAttestationLight computeIfAbsent(
      final Attestation attestation, final Supplier<IndexedAttestationLight> attestationProvider) {
    return indexedAttestations.computeIfAbsent(attestation, __ -> attestationProvider.get());
  }

  public Collection<IndexedAttestationLight> getIndexedAttestations() {
    return indexedAttestations.values();
  }
}
