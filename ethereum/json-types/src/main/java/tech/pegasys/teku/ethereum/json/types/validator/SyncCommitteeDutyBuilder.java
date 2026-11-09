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

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class SyncCommitteeDutyBuilder {
  public static final DeserializableTypeDefinition<SyncCommitteeDuty> SYNC_COMMITTEE_DUTY_TYPE =
      DeserializableTypeDefinition.object(SyncCommitteeDuty.class, SyncCommitteeDutyBuilder.class)
          .initializer(SyncCommitteeDutyBuilder::new)
          .finisher(SyncCommitteeDutyBuilder::build)
          .withField(
              "pubkey",
              PUBLIC_KEY_TYPE,
              SyncCommitteeDuty::getPublicKey,
              SyncCommitteeDutyBuilder::publicKey)
          .withField(
              "validator_index",
              INTEGER_TYPE,
              SyncCommitteeDuty::getValidatorIndex,
              SyncCommitteeDutyBuilder::validatorIndex)
          .withField(
              "validator_sync_committee_indices",
              listOf(INTEGER_TYPE),
              syncCommitteeDuty ->
                  new IntArrayList(syncCommitteeDuty.getValidatorSyncCommitteeIndices()),
              (syncCommitteeDutyBuilder, integers) ->
                  syncCommitteeDutyBuilder.validatorSyncCommitteeIndices(new IntArraySet(integers)))
          .build();

  private BLSPublicKey publicKey;
  private int validatorIndex;
  private IntSet validatorSyncCommitteeIndices;

  public SyncCommitteeDutyBuilder publicKey(final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  public SyncCommitteeDutyBuilder validatorIndex(final int validatorIndex) {
    this.validatorIndex = validatorIndex;
    return this;
  }

  public SyncCommitteeDutyBuilder validatorSyncCommitteeIndices(
      final IntSet validatorSyncCommitteeIndices) {
    this.validatorSyncCommitteeIndices = validatorSyncCommitteeIndices;
    return this;
  }

  public SyncCommitteeDuty build() {
    return new SyncCommitteeDuty(publicKey, validatorIndex, validatorSyncCommitteeIndices);
  }
}
