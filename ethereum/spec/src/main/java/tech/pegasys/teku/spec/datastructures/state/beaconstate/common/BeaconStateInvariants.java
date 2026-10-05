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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class BeaconStateInvariants {
  // Schemas
  static final SszSchema<SszUInt64> GENESIS_TIME_SCHEMA = SszPrimitiveSchemas.UINT64_SCHEMA;
  static final SszSchema<SszBytes32> GENESIS_VALIDATORS_ROOT_SCHEMA =
      SszPrimitiveSchemas.BYTES32_SCHEMA;
  static final SszSchema<SszUInt64> SLOT_SCHEMA = SszPrimitiveSchemas.UINT64_SCHEMA;

  // Fields
  public static final SszField GENESIS_TIME_FIELD =
      new SszField(0, BeaconStateFields.GENESIS_TIME, GENESIS_TIME_SCHEMA);
  public static final SszField GENESIS_VALIDATORS_ROOT_FIELD =
      new SszField(1, BeaconStateFields.GENESIS_VALIDATORS_ROOT, GENESIS_VALIDATORS_ROOT_SCHEMA);
  public static final SszField SLOT_FIELD = new SszField(2, BeaconStateFields.SLOT, SLOT_SCHEMA);

  // Return list of invariant fields
  static List<SszField> getInvariantFields() {
    return List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD, SLOT_FIELD);
  }

  /**
   * Extract the slot value from any serialized state
   *
   * @param bytes A serialized state
   * @return The slot of the state
   */
  public static UInt64 extractSlot(final Bytes bytes) {
    // Check assumptions
    checkState(GENESIS_TIME_SCHEMA.isFixedSize(), "Expected genesisTime field to be a fixed size");
    checkState(
        GENESIS_VALIDATORS_ROOT_SCHEMA.isFixedSize(),
        "Expected genesisValidatorsRoot field to be a fixed size");
    checkState(SLOT_SCHEMA.isFixedSize(), "Expected slot field to be a fixed size");

    final int offset =
        GENESIS_TIME_SCHEMA.getSszFixedPartSize()
            + GENESIS_VALIDATORS_ROOT_SCHEMA.getSszFixedPartSize();
    final int size = SLOT_SCHEMA.getSszFixedPartSize();

    // Extract slot data
    final Bytes slotData = bytes.slice(offset, size);
    return SLOT_SCHEMA.sszDeserialize(slotData).get();
  }

  @SuppressWarnings({"ReferenceComparison", "ReferenceEquality"})
  static boolean equals(final BeaconState state, final Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (state == obj) {
      return true;
    }

    if (obj instanceof final BeaconState other) {

      return state.hashTreeRoot().equals(other.hashTreeRoot());
    } else {
      return false;
    }
  }

  static int hashCode(final BeaconState state) {
    return state.hashTreeRoot().slice(0, 4).toInt();
  }

  static String toString(final BeaconState state, final Consumer<ToStringHelper> modifier) {
    final ToStringHelper builder =
        MoreObjects.toStringHelper(state)
            .add("genesis_time", state.getGenesisTime())
            .add("genesis_validators_root", state.getGenesisValidatorsRoot())
            .add("slot", state.getSlot())
            .add("fork", state.getFork())
            .add("latest_block_header", state.getLatestBlockHeader())
            .add("block_roots", state.getBlockRoots())
            .add("state_roots", state.getStateRoots())
            .add("historical_roots", state.getHistoricalRoots())
            .add("eth1_data", state.getEth1Data())
            .add("eth1_data_votes", state.getEth1DataVotes())
            .add("eth1_deposit_index", state.getEth1DepositIndex())
            .add("validators", state.getValidators())
            .add("balances", state.getBalances())
            .add("randao_mixes", state.getRandaoMixes())
            .add("slashings", state.getSlashings())
            .add("justification_bits", state.getJustificationBits())
            .add("previous_justified_checkpoint", state.getPreviousJustifiedCheckpoint())
            .add("current_justified_checkpoint", state.getCurrentJustifiedCheckpoint())
            .add("finalized_checkpoint", state.getFinalizedCheckpoint());

    modifier.accept(builder);
    return builder.toString();
  }
}
