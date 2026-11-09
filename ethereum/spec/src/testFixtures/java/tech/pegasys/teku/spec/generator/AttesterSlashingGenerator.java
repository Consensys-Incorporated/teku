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

package tech.pegasys.teku.spec.generator;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Committee;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class AttesterSlashingGenerator {
  private final Spec spec;
  private final List<BLSKeyPair> validatorKeys;
  private final DataStructureUtil dataStructureUtil;

  public AttesterSlashingGenerator(final Spec spec, final List<BLSKeyPair> validatorKeys) {
    this.spec = spec;
    this.validatorKeys = validatorKeys;
    this.dataStructureUtil = new DataStructureUtil(spec);
  }

  public AttesterSlashing createAttesterSlashingForAttestation(
      final Attestation goodAttestation, final SignedBlockAndState blockAndState) {
    if (!goodAttestation.getData().getSlot().equals(blockAndState.getSlot())) {
      throw new RuntimeException(
          String.format(
              "Good attestation slot %s and input block slot %s should match",
              goodAttestation.getData().getSlot(), blockAndState.getSlot()));
    }
    AttestationUtil attestationUtil = spec.atSlot(blockAndState.getSlot()).getAttestationUtil();
    final IndexedAttestationSchema indexedAttestationSchema =
        spec.atSlot(blockAndState.getSlot()).getSchemaDefinitions().getIndexedAttestationSchema();
    IndexedAttestationLight indexedGoodAttestationLight =
        attestationUtil.getIndexedAttestation(blockAndState.getState(), goodAttestation);
    IndexedAttestation indexedGoodAttestation =
        indexedGoodAttestationLight.toSsz(indexedAttestationSchema);
    int validatorIndex = indexedGoodAttestationLight.attestingIndices().get(0).intValue();

    UInt64 epoch = spec.computeEpochAtSlot(blockAndState.getSlot());
    Optional<CommitteeAssignment> maybeAssignment =
        spec.getCommitteeAssignment(blockAndState.getState(), epoch, validatorIndex);
    IntList committeeIndices = maybeAssignment.orElseThrow().committee();
    UInt64 committeeIndex = maybeAssignment.orElseThrow().committeeIndex();
    Committee committee = new Committee(committeeIndex, committeeIndices);
    int indexIntoCommittee = committeeIndices.indexOf(validatorIndex);

    AttestationData genericAttestationData =
        spec.getGenericAttestationData(
            blockAndState.getSlot(),
            blockAndState.getState(),
            blockAndState.getBlock(),
            committeeIndex);
    AttestationData brokenAttestationData =
        new AttestationData(
            genericAttestationData.getSlot(),
            genericAttestationData.getIndex(),
            genericAttestationData.getBeaconBlockRoot(),
            genericAttestationData.getSource(),
            new Checkpoint(
                genericAttestationData.getTarget().getEpoch(), dataStructureUtil.randomBytes32()));
    BLSKeyPair validatorKeyPair = validatorKeys.get(validatorIndex);
    Attestation badAttestation =
        createAttestation(
            blockAndState.getState(),
            validatorKeyPair,
            indexIntoCommittee,
            committee,
            brokenAttestationData);
    IndexedAttestation indexedBadAttestation =
        attestationUtil
            .getIndexedAttestation(blockAndState.getState(), badAttestation)
            .toSsz(indexedAttestationSchema);

    return spec.getGenesisSchemaDefinitions()
        .getAttesterSlashingSchema()
        .create(indexedGoodAttestation, indexedBadAttestation);
  }

  private Attestation createAttestation(
      final BeaconState state,
      final BLSKeyPair attesterKeyPair,
      final int indexIntoCommittee,
      final Committee committee,
      final AttestationData attestationData) {
    int committeeSize = committee.getCommitteeSize();
    AttestationSchema<?> attestationSchema =
        spec.atSlot(attestationData.getSlot()).getSchemaDefinitions().getAttestationSchema();
    SszBitlist aggregationBitfield =
        attestationSchema.getAggregationBitsSchema().ofBits(committeeSize, indexIntoCommittee);

    BLSSignature signature =
        new LocalSigner(spec, attesterKeyPair, SyncAsyncRunner.SYNC_RUNNER)
            .signAttestationData(attestationData, state.getForkInfo())
            .join();
    return attestationSchema.create(aggregationBitfield, attestationData, signature);
  }
}
