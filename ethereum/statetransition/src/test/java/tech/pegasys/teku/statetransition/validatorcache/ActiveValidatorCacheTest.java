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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;

public class ActiveValidatorCacheTest {
  private static final UInt64 TWO = UInt64.valueOf(2);
  private static final UInt64 THREE = UInt64.valueOf(3);
  private static final UInt64 FOUR = UInt64.valueOf(4);
  private static final UInt64 FIVE = UInt64.valueOf(5);
  private static final UInt64 SIX = UInt64.valueOf(6);
  final Spec spec = TestSpecFactory.createMinimalAltair();
  final ActiveValidatorCache cache = new ActiveValidatorCache(spec, 10);

  @Test
  void shouldCreateActiveValidatorCache() {
    assertThat(cache.getCacheSize()).isEqualTo(10 + 1000);
    // no entry exists yet for this validator
    assertThat(cache.getValidatorEpochs(ZERO)).isNull();

    // entry got touched, so the array for validator 0 gets created
    // and the epoch reported is stored
    cache.touch(ZERO, ZERO);
    assertThat(cache.getValidatorEpochs(ZERO)).containsExactly(ZERO, null, null);
  }

  @Test
  void shouldStoreSeenEpochsInCache() {
    cache.touch(ZERO, ZERO);
    for (UInt64 i = ZERO; i.isLessThan(SIX); i = i.increment()) {
      cache.touch(ZERO, i);
    }
    // Only the last 3 will be seen, wrapped array can only see 3 entries
    assertThat(cache.getValidatorEpochs(ZERO)).containsExactly(THREE, FOUR, FIVE);
  }

  @Test
  void shouldStoreMostRecentAtOffset() {
    cache.touch(ZERO, ZERO);
    for (UInt64 i = FIVE; i.isGreaterThan(ZERO); i = i.decrement()) {
      cache.touch(ZERO, i);
    }
    // Only the first 3 will be seen, and max logic will override the lower values after
    assertThat(cache.getValidatorEpochs(ZERO)).containsExactly(THREE, FOUR, FIVE);
  }

  @Test
  void shouldGrowCacheByLargeAmount() {
    cache.touch(UInt64.valueOf(100_000), ZERO);
    assertThat(cache.getCacheSize()).isEqualTo(101_000);
    assertThat(cache.getValidatorEpochs(UInt64.valueOf(100_000))).containsExactly(ZERO, null, null);
  }

  @Test
  void shouldGrowCacheIncrementally() {
    cache.touch(UInt64.valueOf(1_100), ZERO);
    assertThat(cache.getCacheSize()).isEqualTo(2_100);
    assertThat(cache.getValidatorEpochs(UInt64.valueOf(1_100))).containsExactly(ZERO, null, null);
  }

  @Test
  void shouldFindValidatorSeenAtEpoch() {
    for (UInt64 i = ZERO; i.isLessThan(FOUR); i = i.increment()) {
      cache.touch(ZERO, i);
    }
    assertThat(cache.isValidatorSeenAtEpoch(ZERO, ZERO)).isFalse();
    assertThat(cache.isValidatorSeenAtEpoch(ZERO, THREE)).isTrue();
  }

  @Test
  void shouldNotGrowStorageForIsSeenRequest() {
    assertThat(cache.isValidatorSeenAtEpoch(UInt64.valueOf(100_000), ZERO)).isFalse();
    assertThat(cache.getCacheSize()).isEqualTo(1010);
  }

  @Test
  void shouldAcceptBeaconBlock() {
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    when(block.getProposerIndex()).thenReturn(ZERO);
    // slot 8 is epoch 1, 16 is epoch 2, 24 is epoch 3
    when(block.getSlot()).thenReturn(UInt64.valueOf(8), UInt64.valueOf(16), UInt64.valueOf(24));
    cache.onBlockImported(block);
    cache.onBlockImported(block);
    cache.onBlockImported(block);
    assertThat(cache.getValidatorEpochs(ZERO)).containsExactly(THREE, ONE, TWO);
  }

  @Test
  void shouldAcceptAttestations() {
    final Attestation attestation = mock(Attestation.class);
    final AttestationData attestationData = mock(AttestationData.class);
    final IndexedAttestationLight indexedAttestation = mock(IndexedAttestationLight.class);
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, attestation);
    validatableAttestation.setIndexedAttestation(indexedAttestation);

    when(indexedAttestation.data()).thenReturn(attestationData);
    when(attestationData.getSlot())
        .thenReturn(UInt64.valueOf(8), UInt64.valueOf(16), UInt64.valueOf(24));

    final List<UInt64> validators = List.of(UInt64.valueOf(11), UInt64.valueOf(21));
    when(indexedAttestation.attestingIndices()).thenReturn(validators);

    // each attestation will have 2 validators.
    // getSlot will return 8, then 16, then 24; and each validator will be processed
    cache.onAttestation(validatableAttestation);
    cache.onAttestation(validatableAttestation);
    cache.onAttestation(validatableAttestation);

    assertThat(cache.getValidatorEpochs(UInt64.valueOf(11))).containsExactly(THREE, ONE, TWO);
    assertThat(cache.getValidatorEpochs(UInt64.valueOf(21))).containsExactly(THREE, ONE, TWO);
  }

  @Test
  void shouldGenerateSeenAtEpoch() throws ExecutionException, InterruptedException {
    cache.touch(ONE, ONE);
    cache.touch(TWO, TWO);
    cache.touch(THREE, THREE);
    cache.touch(ONE, THREE);
    cache.touch(SIX, SIX);

    final SafeFuture<Object2BooleanMap<UInt64>> future =
        cache.validatorsLiveAtEpoch(List.of(ONE, TWO, THREE, FOUR, FIVE, SIX), THREE);
    assertThat(future).isCompleted();
    final Map<UInt64, Boolean> result = future.get();
    assertThat(result)
        .isEqualTo(
            Map.of(
                ONE, true,
                TWO, false,
                THREE, true,
                FOUR, false,
                FIVE, false,
                SIX, false));
  }
}
