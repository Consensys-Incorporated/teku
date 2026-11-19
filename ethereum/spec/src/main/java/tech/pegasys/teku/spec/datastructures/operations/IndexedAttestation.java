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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;

/**
 * An attestation by validator indices. {@link IndexedAttestationPhase0} is BLS-signed, as in every
 * fork up to PQ; {@link
 * tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPq} carries an
 * attestation proof instead, so its {@link #getSignature()} throws.
 */
public interface IndexedAttestation extends SszContainer {

  @Override
  SszContainerSchema<? extends IndexedAttestation> getSchema();

  SszUInt64List getAttestingIndices();

  AttestationData getData();

  /**
   * @throws UnsupportedOperationException for a PQ indexed attestation, which has a proof instead
   */
  BLSSignature getSignature();
}
