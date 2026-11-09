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

package tech.pegasys.teku.spec.datastructures.util;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;

public class BeaconBlockBodyLists {

  public static BeaconBlockBodyLists ofSpecAtSlot(final Spec spec, final UInt64 slot) {
    return new BeaconBlockBodyLists(spec, slot);
  }

  public static BeaconBlockBodyLists ofSpec(final Spec spec) {
    return new BeaconBlockBodyLists(spec, GENESIS_SLOT);
  }

  private final BeaconBlockBodySchema<?> blockBodySchema;

  public BeaconBlockBodyLists(final Spec spec, final UInt64 slot) {
    blockBodySchema = spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  public SszList<ProposerSlashing> createProposerSlashings(
      final ProposerSlashing... proposerSlashings) {
    return blockBodySchema.getProposerSlashingsSchema().of(proposerSlashings);
  }

  public SszList<AttesterSlashing> createAttesterSlashings(
      final AttesterSlashing... attesterSlashings) {
    return blockBodySchema.getAttesterSlashingsSchema().of(attesterSlashings);
  }

  public SszList<Attestation> createAttestations(final Attestation... attestations) {
    return blockBodySchema.getAttestationsSchema().of(attestations);
  }

  public SszList<Deposit> createDeposits(final Deposit... deposits) {
    return blockBodySchema.getDepositsSchema().of(deposits);
  }

  public SszList<SignedVoluntaryExit> createVoluntaryExits(
      final SignedVoluntaryExit... voluntaryExits) {
    return blockBodySchema.getVoluntaryExitsSchema().of(voluntaryExits);
  }

  public SszList<SignedBlsToExecutionChange> createBlsToExecutionChanges(
      final SignedBlsToExecutionChange... blsToExecutionChanges) {
    return blockBodySchema
        .toVersionCapella()
        .map(schema -> schema.getBlsToExecutionChangesSchema().of(blsToExecutionChanges))
        .orElse(null);
  }

  public SszList<PayloadAttestation> createPayloadAttestations(
      final PayloadAttestation... payloadAttestations) {
    return blockBodySchema
        .toVersionGloas()
        .map(schema -> schema.getPayloadAttestationsSchema().of(payloadAttestations))
        .orElse(null);
  }
}
