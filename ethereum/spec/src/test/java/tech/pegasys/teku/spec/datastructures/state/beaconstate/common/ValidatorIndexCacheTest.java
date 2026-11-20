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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ValidatorIndexCacheTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  final BeaconState state = dataStructureUtil.randomBeaconState();
  final BLSPublicKey missingPublicKey = dataStructureUtil.randomPublicKey();

  @SuppressWarnings("unchecked")
  final Cache<BLSPublicKey, Integer> cache = mock(Cache.class);

  @Test
  public void shouldNotScanStateIfAlreadyHaveValidators() {
    final ValidatorIndexCache validatorIndexCache =
        new ValidatorIndexCache(cache, state.getValidators().size());

    when(cache.getCached(missingPublicKey)).thenReturn(Optional.empty());
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);

    verify(cache).getCached(missingPublicKey);
    verify(cache, never()).invalidateWithNewValue(any(), any());
    assertThat(index).isEmpty();
  }

  @Test
  public void shouldScanNewValidatorsInSuppliedState() {
    final ValidatorIndexCache validatorIndexCache =
        new ValidatorIndexCache(cache, state.getValidators().size() - 5);

    when(cache.getCached(missingPublicKey)).thenReturn(Optional.empty());
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);
    verify(cache).getCached(missingPublicKey);
    verify(cache, times(4)).invalidateWithNewValue(any(), any());
    assertThat(index).isEmpty();
  }

  @Test
  public void shouldGetAllValidatorKeysCachedIfMissingKeyPassed() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);
    assertThat(index).isEmpty();
    assertThat(validatorIndexCache.getValidatorIndices().size())
        .isEqualTo(state.getValidators().size());
  }

  @Test
  public void shouldPopulateCacheItemsFromState() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    final BLSPublicKey foundKey =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(10).getPubkeyBytes());

    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, foundKey);
    assertThat(index.get()).isEqualTo(10);
    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(10);
    assertThat(validatorIndexCache.getValidatorIndices().size()).isEqualTo(11);
  }

  @Test
  public void shouldFilterItemsBeyondStateIndex() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    validatorIndexCache.invalidateWithNewValue(missingPublicKey, 100);
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);

    assertThat(index).isEmpty();
    // state didn't get scanned, because we had the index but it was out of bounds
    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(-1);
    assertThat(validatorIndexCache.getValidatorIndices().size()).isEqualTo(1);
  }

  @Test
  public void shouldFindPqValidatorsByXmssKey() {
    final BeaconState pqState = pqState(pqKey(0), pqKey(1), pqKey(2));
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();

    assertThat(validatorIndexCache.getValidatorIndexByXmssKey(pqState, pqKey(2))).contains(2);
    // Scanning for index 2 cached the keys before it
    assertThat(validatorIndexCache.getValidatorIndexByXmssKey(pqState, pqKey(1))).contains(1);
    assertThat(validatorIndexCache.getValidatorIndexByXmssKey(pqState, pqKey(3))).isEmpty();
  }

  @Test
  public void shouldFindPqValidatorsAppendedAfterAnEarlierLookup() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    assertThat(validatorIndexCache.getValidatorIndexByXmssKey(pqState(pqKey(0)), pqKey(1)))
        .isEmpty();

    assertThat(
            validatorIndexCache.getValidatorIndexByXmssKey(pqState(pqKey(0), pqKey(1)), pqKey(1)))
        .contains(1);
  }

  @Test
  public void shouldNotUseAnXmssIndexCachedFromAnotherBranch() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    // One branch appended key 2 at index 1, another key 3, then key 2 at index 2
    assertThat(
            validatorIndexCache.getValidatorIndexByXmssKey(pqState(pqKey(0), pqKey(2)), pqKey(2)))
        .contains(1);
    final BeaconState otherBranch = pqState(pqKey(0), pqKey(3), pqKey(2));

    assertThat(validatorIndexCache.getValidatorIndexByXmssKey(otherBranch, pqKey(2))).contains(2);
    assertThat(validatorIndexCache.getValidatorIndexByXmssKey(otherBranch, pqKey(3))).contains(1);
  }

  @Test
  public void shouldFindNoBlsKeyInAPqState() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();

    assertThat(validatorIndexCache.getValidatorIndex(pqState(pqKey(0)), missingPublicKey))
        .isEmpty();
  }

  @Test
  public void shouldFindPqValidatorsWithoutCaching() {
    final BeaconState pqState = pqState(pqKey(0), pqKey(1));

    assertThat(ValidatorIndexCache.NO_OP_INSTANCE.getValidatorIndexByXmssKey(pqState, pqKey(1)))
        .contains(1);
    assertThat(ValidatorIndexCache.NO_OP_INSTANCE.getValidatorIndexByXmssKey(pqState, pqKey(0)))
        .contains(0);
  }

  private static Bytes32 pqKey(final int i) {
    return Bytes32.fromHexStringLenient(Integer.toHexString(i + 1));
  }

  private BeaconState pqState(final Bytes32... keys) {
    final Spec pqSpec = TestSpecFactory.createMinimalPq();
    return BeaconStatePq.required(
            SchemaDefinitionsPq.required(
                    pqSpec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions())
                .getPqBeaconStateSchema()
                .createEmpty())
        .updatedPq(
            mutable -> {
              for (final Bytes32 key : keys) {
                mutable
                    .getValidators()
                    .append(
                        new ValidatorPq(
                            key,
                            Bytes32.ZERO,
                            Bytes32.ZERO,
                            UInt64.ZERO,
                            false,
                            UInt64.ZERO,
                            UInt64.ZERO,
                            UInt64.MAX_VALUE,
                            UInt64.MAX_VALUE));
              }
            });
  }
}
