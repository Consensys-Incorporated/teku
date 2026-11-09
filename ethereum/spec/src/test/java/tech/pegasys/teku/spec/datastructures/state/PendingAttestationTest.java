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

package tech.pegasys.teku.spec.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation.PendingAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PendingAttestationTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final PendingAttestationSchema schema =
      BeaconStateSchemaPhase0.required(spec.getGenesisSchemaDefinitions().getBeaconStateSchema())
          .getPendingAttestationSchema();
  private final SszBitlist participationBitfield = dataStructureUtil.randomBitlist();
  private final AttestationData data = dataStructureUtil.randomAttestationData();
  private final UInt64 inclusionDelay = dataStructureUtil.randomUInt64();
  private final UInt64 proposerIndex = dataStructureUtil.randomUInt64();

  private final PendingAttestation pendingAttestation =
      schema.create(participationBitfield, data, inclusionDelay, proposerIndex);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    PendingAttestation testPendingAttestation = pendingAttestation;

    assertEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    PendingAttestation testPendingAttestation =
        schema.create(participationBitfield, data, inclusionDelay, proposerIndex);

    assertEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // BeaconBlock is rather involved to create. Just create a random one until it is not the same
    // as the original.
    AttestationData otherAttestationData = dataStructureUtil.randomAttestationData();
    while (Objects.equals(otherAttestationData, data)) {
      otherAttestationData = dataStructureUtil.randomAttestationData();
    }
    PendingAttestation testPendingAttestation =
        schema.create(participationBitfield, otherAttestationData, inclusionDelay, proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenParticipationBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        schema.create(dataStructureUtil.randomBitlist(), data, inclusionDelay, proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        schema.create(
            participationBitfield,
            data,
            inclusionDelay.plus(dataStructureUtil.randomUInt64()),
            proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndicesAreDifferent() {
    PendingAttestation testPendingAttestation =
        schema.create(
            participationBitfield,
            data,
            inclusionDelay,
            proposerIndex.plus(dataStructureUtil.randomUInt64()));

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void testSszRoundtripWithEmptyBitlist() {
    PendingAttestation testPendingAttestation =
        schema.create(
            schema.getAggregationBitfieldSchema().empty(),
            data,
            inclusionDelay,
            proposerIndex.plus(dataStructureUtil.randomUInt64()));
    Bytes ssz = testPendingAttestation.sszSerialize();
    PendingAttestation attestation = schema.sszDeserialize(ssz);
    assertEquals(testPendingAttestation, attestation);
  }
}
