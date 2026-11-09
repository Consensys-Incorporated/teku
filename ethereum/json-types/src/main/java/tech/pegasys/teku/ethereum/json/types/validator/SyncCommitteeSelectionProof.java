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

public class SyncCommitteeSelectionProof {

  public static final DeserializableTypeDefinition<SyncCommitteeSelectionProof>
      SYNC_COMMITTEE_SELECTION_PROOF =
          DeserializableTypeDefinition.object(
                  SyncCommitteeSelectionProof.class, SyncCommitteeSelectionProof.Builder.class)
              .name("SyncCommitteeSelectionProof")
              .initializer(SyncCommitteeSelectionProof::builder)
              .finisher(SyncCommitteeSelectionProof.Builder::build)
              .withField(
                  "validator_index",
                  CoreTypes.INTEGER_TYPE,
                  SyncCommitteeSelectionProof::getValidatorIndex,
                  SyncCommitteeSelectionProof.Builder::validatorIndex)
              .withField(
                  "slot",
                  CoreTypes.UINT64_TYPE,
                  SyncCommitteeSelectionProof::getSlot,
                  SyncCommitteeSelectionProof.Builder::slot)
              .withField(
                  "subcommittee_index",
                  CoreTypes.INTEGER_TYPE,
                  SyncCommitteeSelectionProof::getSubcommitteeIndex,
                  SyncCommitteeSelectionProof.Builder::subcommitteeIndex)
              .withField(
                  "selection_proof",
                  CoreTypes.STRING_TYPE,
                  SyncCommitteeSelectionProof::getSelectionProof,
                  SyncCommitteeSelectionProof.Builder::selectionProof)
              .build();

  private final int validatorIndex;
  private final UInt64 slot;
  private final int subcommitteeIndex;
  private final String selectionProof;

  private SyncCommitteeSelectionProof(
      final int validatorIndex,
      final UInt64 slot,
      final int subcommitteeIndex,
      final String selectionProof) {
    this.validatorIndex = validatorIndex;
    this.slot = slot;
    this.subcommitteeIndex = subcommitteeIndex;
    this.selectionProof = selectionProof;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public UInt64 getSlot() {
    return slot;
  }

  public int getSubcommitteeIndex() {
    return subcommitteeIndex;
  }

  public String getSelectionProof() {
    return selectionProof;
  }

  public BLSSignature getSelectionProofSignature() {
    return BLSSignature.fromBytesCompressed(Bytes.fromHexString(getSelectionProof()));
  }

  public static SyncCommitteeSelectionProof.Builder builder() {
    return new SyncCommitteeSelectionProof.Builder();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SyncCommitteeSelectionProof that = (SyncCommitteeSelectionProof) o;
    return validatorIndex == that.validatorIndex
        && subcommitteeIndex == that.subcommitteeIndex
        && Objects.equals(slot, that.slot)
        && Objects.equals(selectionProof, that.selectionProof);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, slot, subcommitteeIndex, selectionProof);
  }

  public static class Builder {

    private int validatorIndex;
    private UInt64 slot;
    private int subcommitteeIndex;
    private String selectionProof;

    public Builder validatorIndex(final int validatorIndex) {
      this.validatorIndex = validatorIndex;
      return this;
    }

    public Builder subcommitteeIndex(final int subcommitteeIndex) {
      this.subcommitteeIndex = subcommitteeIndex;
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

    public SyncCommitteeSelectionProof build() {
      return new SyncCommitteeSelectionProof(
          validatorIndex, slot, subcommitteeIndex, selectionProof);
    }
  }
}
