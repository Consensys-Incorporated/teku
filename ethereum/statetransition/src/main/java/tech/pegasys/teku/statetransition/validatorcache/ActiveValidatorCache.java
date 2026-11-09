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

package tech.pegasys.teku.statetransition.validatorcache;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class ActiveValidatorCache implements ActiveValidatorChannel {
  private final Spec spec;
  private static final Logger LOG = LogManager.getLogger();
  private static final int VALIDATOR_CACHE_SPARE_CAPACITY = 1000;
  public static final int TRACKED_EPOCHS = 2;

  // We will definitely receive events for the current epoch,
  // and we want a certain number of FULL epochs of tracking to query,
  // so the cache size will actually be the TRACKED_EPOCHS + 1
  private static final int CACHED_EPOCHS = TRACKED_EPOCHS + 1;

  // validatorActiveEpochs is indexed by validator id, contains mod(CACHED_EPOCHS) of epoch values
  // - basically a rotating list
  private UInt64[][] validatorActiveEpochs;

  public ActiveValidatorCache(final Spec spec, final int initialSize) {
    checkArgument(initialSize >= 0);
    validatorActiveEpochs = new UInt64[initialSize + VALIDATOR_CACHE_SPARE_CAPACITY][];
    this.spec = spec;
  }

  void touch(final UInt64 validatorIndex, final UInt64 epoch) {
    LOG.trace("Touch validator {} at epoch {}", validatorIndex, epoch);

    ensureNodeIsInitialized(validatorIndex);
    final int index = validatorIndex.intValue();
    final int offset = epoch.mod(CACHED_EPOCHS).intValue();
    if (validatorActiveEpochs[index][offset] == null) {
      validatorActiveEpochs[index][offset] = epoch;
    } else {
      // Given the chain only ever goes forward,
      // older epochs are not important in this cache
      validatorActiveEpochs[index][offset] = validatorActiveEpochs[index][offset].max(epoch);
    }
  }

  boolean isValidatorSeenAtEpoch(final UInt64 validatorIndex, final UInt64 epoch) {
    if (validatorActiveEpochs.length <= validatorIndex.intValue()) {
      LOG.trace(
          "validator index {} exceeds array size {}", validatorIndex, validatorActiveEpochs.length);
      return false;
    }

    return validatorActiveEpochs[validatorIndex.intValue()] != null
        && epoch.equals(
            validatorActiveEpochs[validatorIndex.intValue()][epoch.mod(CACHED_EPOCHS).intValue()]);
  }

  int getCacheSize() {
    return validatorActiveEpochs.length;
  }

  @VisibleForTesting
  UInt64[] getValidatorEpochs(final UInt64 validatorIndex) {
    checkArgument(validatorIndex.intValue() < validatorActiveEpochs.length);
    return validatorActiveEpochs[validatorIndex.intValue()];
  }

  private void ensureNodeIsInitialized(final UInt64 validatorIndex) {
    if (validatorIndex.intValue() >= validatorActiveEpochs.length) {
      grow(validatorIndex);
    }

    if (validatorActiveEpochs[validatorIndex.intValue()] == null) {
      validatorActiveEpochs[validatorIndex.intValue()] = new UInt64[CACHED_EPOCHS];
    }
  }

  private void grow(final UInt64 requestedValidatorIndex) {
    final int newSize = requestedValidatorIndex.intValue() + VALIDATOR_CACHE_SPARE_CAPACITY;
    LOG.trace(
        "Growing ActiveValidatorCache from {} to {} elements",
        validatorActiveEpochs.length,
        newSize);
    validatorActiveEpochs = Arrays.copyOf(validatorActiveEpochs, newSize);
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block) {
    touch(block.getProposerIndex(), spec.computeEpochAtSlot(block.getSlot()));
  }

  @Override
  public void onAttestation(final ValidatableAttestation validatableAttestation) {
    validatableAttestation
        .getIndexedAttestation()
        .ifPresent(
            attestation -> {
              final UInt64 epoch = spec.computeEpochAtSlot(attestation.data().getSlot());
              for (final UInt64 validatorIndex : attestation.attestingIndices()) {
                touch(validatorIndex, epoch);
              }
            });
  }

  @Override
  public SafeFuture<Object2BooleanMap<UInt64>> validatorsLiveAtEpoch(
      final List<UInt64> validators, final UInt64 epoch) {
    final Object2BooleanMap<UInt64> result = new Object2BooleanArrayMap<>();
    for (UInt64 validator : validators) {
      result.put(validator, isValidatorSeenAtEpoch(validator, epoch));
    }

    return SafeFuture.completedFuture(result);
  }
}
