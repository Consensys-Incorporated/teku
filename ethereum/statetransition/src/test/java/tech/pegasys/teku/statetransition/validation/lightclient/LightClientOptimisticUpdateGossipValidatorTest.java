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

package tech.pegasys.teku.statetransition.validation.lightclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientOptimisticUpdateGossipValidatorTest {

  private static final UInt64 SIGNATURE_SLOT = UInt64.valueOf(10);

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore forkChoiceStore = mock(UpdatableStore.class);
  private final LightClientUpdateStore store = mock(LightClientUpdateStore.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientOptimisticUpdateGossipValidator validator;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    when(recentChainData.getGenesisTimeMillis()).thenReturn(UInt64.ZERO);
    when(recentChainData.getStore()).thenReturn(forkChoiceStore);
    when(store.getLatestOptimisticUpdate()).thenReturn(Optional.empty());
    setTimeMillis(syncMessageDueMillis());
    validator = new LightClientOptimisticUpdateGossipValidator(spec, recentChainData, store);
  }

  @TestTemplate
  void shouldAcceptUpdateMatchingLocalUpdate() {
    final LightClientOptimisticUpdate update = optimisticUpdate(9);
    setLocalUpdate(update);

    assertResult(update, ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreWhenThereIsNoLocalUpdate() {
    assertResult(optimisticUpdate(9), IGNORE);
  }

  @TestTemplate
  void shouldIgnoreUpdateNotMatchingLocalUpdate() {
    setLocalUpdate(optimisticUpdate(9));

    assertResult(optimisticUpdate(9), IGNORE);
  }

  @TestTemplate
  void shouldIgnoreUpdateReceivedBeforeSyncMessageDue() {
    final LightClientOptimisticUpdate update = optimisticUpdate(9);
    setLocalUpdate(update);
    setTimeMillis(
        syncMessageDueMillis()
            .minus(spec.getNetworkingConfig().getMaximumGossipClockDisparity() + 1));

    assertResult(update, IGNORE);
  }

  @TestTemplate
  void shouldIgnoreUpdateWithSameAttestedSlotAsForwarded() {
    final LightClientOptimisticUpdate first = optimisticUpdate(9);
    setLocalUpdate(first);
    assertResult(first, ACCEPT);

    final LightClientOptimisticUpdate second = optimisticUpdate(9);
    setLocalUpdate(second);
    assertResult(second, IGNORE);
  }

  @TestTemplate
  void shouldAcceptUpdateWithHigherAttestedSlotThanForwarded() {
    final LightClientOptimisticUpdate older = optimisticUpdate(8);
    setLocalUpdate(older);
    assertResult(older, ACCEPT);

    final LightClientOptimisticUpdate newer = optimisticUpdate(9);
    setLocalUpdate(newer);
    assertResult(newer, ACCEPT);
  }

  @TestTemplate
  void markForwarded_shouldPreventForwardingSameUpdateFromPeers() {
    final LightClientOptimisticUpdate update = optimisticUpdate(9);
    setLocalUpdate(update);

    assertThat(validator.markForwarded(update)).isTrue();
    assertThat(validator.markForwarded(update)).isFalse();
    assertResult(update, IGNORE);
  }

  private void assertResult(
      final LightClientOptimisticUpdate update, final ValidationResultCode expectedCode) {
    assertThatSafeFuture(validator.validate(update))
        .isCompletedWithValueMatching(result -> result.code() == expectedCode);
  }

  private LightClientOptimisticUpdate optimisticUpdate(final long attestedSlot) {
    return dataStructureUtil.randomLightClientOptimisticUpdate(
        UInt64.valueOf(attestedSlot), SIGNATURE_SLOT);
  }

  private void setLocalUpdate(final LightClientOptimisticUpdate update) {
    when(store.getLatestOptimisticUpdate()).thenReturn(Optional.of(update));
  }

  private UInt64 syncMessageDueMillis() {
    return spec.computeTimeMillisAtSlot(SIGNATURE_SLOT, UInt64.ZERO)
        .plus(spec.getSyncMessageDueMillis(SIGNATURE_SLOT));
  }

  private void setTimeMillis(final UInt64 timeMillis) {
    when(forkChoiceStore.getTimeInMillis()).thenReturn(timeMillis);
  }
}
