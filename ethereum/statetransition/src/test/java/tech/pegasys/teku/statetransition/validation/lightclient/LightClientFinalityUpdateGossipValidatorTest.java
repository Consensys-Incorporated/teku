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
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientFinalityUpdateGossipValidatorTest {

  private static final UInt64 SIGNATURE_SLOT = UInt64.valueOf(10);

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore forkChoiceStore = mock(UpdatableStore.class);
  private final LightClientUpdateStore store = mock(LightClientUpdateStore.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientFinalityUpdateGossipValidator validator;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    when(recentChainData.getGenesisTimeMillis()).thenReturn(UInt64.ZERO);
    when(recentChainData.getStore()).thenReturn(forkChoiceStore);
    when(store.getLatestFinalityUpdate()).thenReturn(Optional.empty());
    setTimeMillis(syncMessageDueMillis());
    validator = new LightClientFinalityUpdateGossipValidator(spec, recentChainData, store);
  }

  @TestTemplate
  void shouldAcceptUpdateMatchingLocalUpdate() {
    final LightClientFinalityUpdate update = finalityUpdate(5, fullParticipation());
    setLocalUpdate(update);

    assertResult(update, ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreWhenThereIsNoLocalUpdate() {
    assertResult(finalityUpdate(5, fullParticipation()), IGNORE);
  }

  @TestTemplate
  void shouldIgnoreUpdateNotMatchingLocalUpdate() {
    setLocalUpdate(finalityUpdate(5, fullParticipation()));

    assertResult(finalityUpdate(5, fullParticipation()), IGNORE);
  }

  @TestTemplate
  void shouldIgnoreUpdateReceivedBeforeSyncMessageDue() {
    final LightClientFinalityUpdate update = finalityUpdate(5, fullParticipation());
    setLocalUpdate(update);
    setTimeMillis(syncMessageDueMillis().minus(maximumGossipClockDisparity() + 1));

    assertResult(update, IGNORE);
  }

  @TestTemplate
  void shouldAcceptUpdateReceivedWithinClockDisparityOfSyncMessageDue() {
    final LightClientFinalityUpdate update = finalityUpdate(5, fullParticipation());
    setLocalUpdate(update);
    setTimeMillis(syncMessageDueMillis().minus(maximumGossipClockDisparity()));

    assertResult(update, ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreUpdateWithSignatureSlotFarInTheFuture() {
    final LightClientFinalityUpdate update =
        finalityUpdate(5, fullParticipation(), UInt64.MAX_VALUE);
    setLocalUpdate(update);

    assertResult(update, IGNORE);
  }

  @TestTemplate
  void shouldIgnoreAlreadyForwardedUpdate() {
    final LightClientFinalityUpdate update = finalityUpdate(5, fullParticipation());
    setLocalUpdate(update);
    assertResult(update, ACCEPT);

    assertResult(update, IGNORE);
  }

  @TestTemplate
  void shouldIgnoreUpdateWithOlderFinalizedSlotThanForwarded() {
    final LightClientFinalityUpdate newer = finalityUpdate(6, fullParticipation());
    setLocalUpdate(newer);
    assertResult(newer, ACCEPT);

    final LightClientFinalityUpdate older = finalityUpdate(5, fullParticipation());
    setLocalUpdate(older);
    assertResult(older, IGNORE);
  }

  @TestTemplate
  void shouldAcceptSameFinalizedSlotWhenGainingSupermajority() {
    final LightClientFinalityUpdate withoutSupermajority =
        finalityUpdate(5, dataStructureUtil.randomSyncAggregate(0));
    setLocalUpdate(withoutSupermajority);
    assertResult(withoutSupermajority, ACCEPT);

    final LightClientFinalityUpdate withSupermajority = finalityUpdate(5, fullParticipation());
    setLocalUpdate(withSupermajority);
    assertResult(withSupermajority, ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreSameFinalizedSlotWhenForwardedAlreadyHadSupermajority() {
    final LightClientFinalityUpdate first = finalityUpdate(5, fullParticipation());
    setLocalUpdate(first);
    assertResult(first, ACCEPT);

    final LightClientFinalityUpdate second = finalityUpdate(5, fullParticipation());
    setLocalUpdate(second);
    assertResult(second, IGNORE);
  }

  @TestTemplate
  void markForwarded_shouldPreventForwardingSameUpdateFromPeers() {
    final LightClientFinalityUpdate update = finalityUpdate(5, fullParticipation());
    setLocalUpdate(update);

    assertThat(validator.markForwarded(update)).isTrue();
    assertThat(validator.markForwarded(update)).isFalse();
    assertResult(update, IGNORE);
  }

  private void assertResult(
      final LightClientFinalityUpdate update, final ValidationResultCode expectedCode) {
    assertThatSafeFuture(validator.validate(update))
        .isCompletedWithValueMatching(result -> result.code() == expectedCode);
  }

  private LightClientFinalityUpdate finalityUpdate(
      final long finalizedSlot, final SyncAggregate syncAggregate) {
    return finalityUpdate(finalizedSlot, syncAggregate, SIGNATURE_SLOT);
  }

  private LightClientFinalityUpdate finalityUpdate(
      final long finalizedSlot, final SyncAggregate syncAggregate, final UInt64 signatureSlot) {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(
            SIGNATURE_SLOT.minus(1), UInt64.valueOf(finalizedSlot));
    return SchemaDefinitionsAltair.required(spec.atSlot(SIGNATURE_SLOT).getSchemaDefinitions())
        .getLightClientFinalityUpdateSchema()
        .create(
            update.getAttestedHeader(),
            update.getFinalizedHeader(),
            update.getFinalityBranch(),
            syncAggregate,
            SszUInt64.of(signatureSlot));
  }

  private SyncAggregate fullParticipation() {
    final int syncCommitteeSize =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getSyncCommitteeSize();
    return dataStructureUtil.randomSyncAggregate(IntStream.range(0, syncCommitteeSize).toArray());
  }

  private void setLocalUpdate(final LightClientFinalityUpdate update) {
    when(store.getLatestFinalityUpdate()).thenReturn(Optional.of(update));
  }

  private UInt64 syncMessageDueMillis() {
    return spec.computeTimeMillisAtSlot(SIGNATURE_SLOT, UInt64.ZERO)
        .plus(spec.getSyncMessageDueMillis(SIGNATURE_SLOT));
  }

  private void setTimeMillis(final UInt64 timeMillis) {
    when(forkChoiceStore.getTimeInMillis()).thenReturn(timeMillis);
  }

  private int maximumGossipClockDisparity() {
    return spec.getNetworkingConfig().getMaximumGossipClockDisparity();
  }
}
