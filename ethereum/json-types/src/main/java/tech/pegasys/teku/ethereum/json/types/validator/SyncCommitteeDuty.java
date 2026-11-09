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

import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Objects;
import tech.pegasys.teku.bls.BLSPublicKey;

public class SyncCommitteeDuty {

  private final BLSPublicKey publicKey;
  private final int validatorIndex;
  private final IntSet validatorSyncCommitteeIndices;

  public SyncCommitteeDuty(
      final BLSPublicKey publicKey,
      final int validatorIndex,
      final IntSet validatorSyncCommitteeIndices) {
    this.publicKey = publicKey;
    this.validatorIndex = validatorIndex;
    this.validatorSyncCommitteeIndices = validatorSyncCommitteeIndices;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public IntSet getValidatorSyncCommitteeIndices() {
    return validatorSyncCommitteeIndices;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SyncCommitteeDuty that = (SyncCommitteeDuty) o;
    return validatorIndex == that.validatorIndex
        && Objects.equals(validatorSyncCommitteeIndices, that.validatorSyncCommitteeIndices)
        && Objects.equals(publicKey, that.publicKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey, validatorIndex, validatorSyncCommitteeIndices);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("validatorIndex", validatorIndex)
        .add("syncCommitteeIndex", validatorSyncCommitteeIndices)
        .toString();
  }
}
