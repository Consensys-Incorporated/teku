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

package tech.pegasys.teku.ethereum.json.types.validator;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconCommitteeSelectionProof {

  public static final DeserializableTypeDefinition<BeaconCommitteeSelectionProof>
      BEACON_COMMITTEE_SELECTION_PROOF =
          DeserializableTypeDefinition.object(
                  BeaconCommitteeSelectionProof.class, BeaconCommitteeSelectionProof.Builder.class)
              .name("BeaconCommitteeSelectionProof")
              .initializer(BeaconCommitteeSelectionProof::builder)
              .finisher(BeaconCommitteeSelectionProof.Builder::build)
              .withField(
                  "validator_index",
                  CoreTypes.INTEGER_TYPE,
                  BeaconCommitteeSelectionProof::getValidatorIndex,
                  BeaconCommitteeSelectionProof.Builder::validatorIndex)
              .withField(
                  "slot",
                  CoreTypes.UINT64_TYPE,
                  BeaconCommitteeSelectionProof::getSlot,
                  BeaconCommitteeSelectionProof.Builder::slot)
              .withField(
                  "selection_proof",
                  CoreTypes.STRING_TYPE,
                  BeaconCommitteeSelectionProof::getSelectionProof,
                  BeaconCommitteeSelectionProof.Builder::selectionProof)
              .build();

  private final int validatorIndex;
  private final UInt64 slot;
  private final String selectionProof;

  private BeaconCommitteeSelectionProof(
      final int validatorIndex, final UInt64 slot, final String selectionProof) {
    this.validatorIndex = validatorIndex;
    this.slot = slot;
    this.selectionProof = selectionProof;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public UInt64 getSlot() {
    return slot;
  }

  public String getSelectionProof() {
    return selectionProof;
  }

  public BLSSignature getSelectionProofSignature() {
    return BLSSignature.fromBytesCompressed(Bytes.fromHexString(getSelectionProof()));
  }

  public static BeaconCommitteeSelectionProof.Builder builder() {
    return new BeaconCommitteeSelectionProof.Builder();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconCommitteeSelectionProof that = (BeaconCommitteeSelectionProof) o;
    return validatorIndex == that.validatorIndex
        && Objects.equals(slot, that.slot)
        && Objects.equals(selectionProof, that.selectionProof);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, slot, selectionProof);
  }

  public static class Builder {

    private int validatorIndex;
    private UInt64 slot;
    private String selectionProof;

    public Builder validatorIndex(final int validatorIndex) {
      this.validatorIndex = validatorIndex;
      return this;
    }

    public Builder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    public Builder selectionProof(final String selectionProof) {
      this.selectionProof = selectionProof;
      return this;
    }

    public BeaconCommitteeSelectionProof build() {
      return new BeaconCommitteeSelectionProof(validatorIndex, slot, selectionProof);
    }
  }
}
