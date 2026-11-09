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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PendingAttestationPoolTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AttestationSchema<?> attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  private final PendingAttestationPool pendingAttestationPool =
      new PoolFactory(new StubMetricsSystem()).createPendingAttestationPool(spec, 10);
  private final UInt64 currentSlot = UInt64.valueOf(10);

  @BeforeEach
  public void setup() {
    pendingAttestationPool.onSlot(currentSlot);
  }

  @Test
  public void containsChecksBothPools() {
    final ValidatableAttestation waitingForBlock = validatableAttestation(currentSlot);
    final ValidatableAttestation waitingForPayload = validatableAttestation(currentSlot);

    assertThat(pendingAttestationPool.contains(waitingForBlock)).isFalse();
    assertThat(pendingAttestationPool.contains(waitingForPayload)).isFalse();

    pendingAttestationPool.addForMissingBlock(waitingForBlock);
    pendingAttestationPool.addForMissingFullPayload(waitingForPayload);

    assertThat(pendingAttestationPool.contains(waitingForBlock)).isTrue();
    assertThat(pendingAttestationPool.contains(waitingForPayload)).isTrue();
  }

  @Test
  public void removeAttestationsWaitingForFullPayloadReturnsByBeaconBlockRoot() {
    final ValidatableAttestation attestation = validatableAttestation(currentSlot);
    pendingAttestationPool.addForMissingFullPayload(attestation);

    final Bytes32 blockRoot = attestation.getData().getBeaconBlockRoot();
    assertThat(pendingAttestationPool.removeAttestationsWaitingForFullPayload(blockRoot))
        .containsExactly(attestation);
    assertThat(pendingAttestationPool.removeAttestationsWaitingForFullPayload(blockRoot)).isEmpty();
    assertThat(pendingAttestationPool.contains(attestation)).isFalse();
  }

  @Test
  public void requiredFullPayloadSubscriberFiresForBeaconBlockRoot() {
    final List<Bytes32> required = new ArrayList<>();
    pendingAttestationPool.subscribeRequiredFullPayload(required::add);
    final ValidatableAttestation attestation = validatableAttestation(currentSlot);

    pendingAttestationPool.addForMissingFullPayload(attestation);

    assertThat(required).containsExactly(attestation.getData().getBeaconBlockRoot());
  }

  private ValidatableAttestation validatableAttestation(final UInt64 slot) {
    final AttestationData data =
        new AttestationData(
            slot,
            UInt64.ZERO,
            dataStructureUtil.randomBytes32(),
            new Checkpoint(UInt64.ZERO, Bytes32.ZERO),
            new Checkpoint(spec.computeEpochAtSlot(slot), Bytes32.ZERO));
    return ValidatableAttestation.from(
        spec,
        attestationSchema.create(
            attestationSchema.getAggregationBitsSchema().ofBits(1, 0),
            data,
            BLSSignature.empty(),
            () -> attestationSchema.createEmptyCommitteeBits().orElseThrow()));
  }
}
