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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.VoluntaryExitGenerator;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class VoluntaryExitTopicHandlerTest extends AbstractTopicHandlerTest<SignedVoluntaryExit> {

  private final VoluntaryExitGenerator exitGenerator =
      new VoluntaryExitGenerator(spec, storageSystem.chainBuilder().getValidatorKeys());
  private final int validEpoch = spec.computeEpochAtSlot(validSlot).intValue();

  @Override
  protected Eth2TopicHandler<?> createHandler() {
    return new VoluntaryExitGossipManager(
            recentChainData,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            processor,
            spec.getNetworkingConfig(),
            DebugDataDumper.NOOP)
        .getTopicHandler();
  }

  @Override
  protected StorageSystem createStorageSystem() {
    return InMemoryStorageSystemBuilder.create().numberOfValidators(5).specProvider(spec).build();
  }

  @Test
  public void handleMessage_validExit() {
    final SignedVoluntaryExit exit = exitGenerator.withEpoch(getBestState(), validEpoch, 3);
    when(processor.process(exit, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  // exit epochs falling in Capella, Deneb and Electra respectively
  @ParameterizedTest(name = "exitEpoch={0}")
  @ValueSource(ints = {1, 2, 3})
  public void handleMessage_acceptsPastEpochExitOnFuluTopic(final int exitEpoch) {
    final UInt64 fuluEpoch = UInt64.valueOf(4);
    final Spec fuluSpec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), fuluEpoch);
    final StorageSystem fuluStorage =
        InMemoryStorageSystemBuilder.create().numberOfValidators(5).specProvider(fuluSpec).build();
    fuluStorage.chainUpdater().initializeGenesis();
    final UInt64 headSlot = fuluSpec.computeStartSlotAtEpoch(UInt64.valueOf(65));
    fuluStorage
        .chainUpdater()
        .updateBestBlock(fuluStorage.chainUpdater().advanceChainUntil(headSlot));
    final RecentChainData fuluChainData = fuluStorage.recentChainData();
    final BeaconState state = safeJoin(fuluChainData.getBestState().orElseThrow());
    assertThat(fuluSpec.atSlot(state.getSlot()).getMilestone()).isEqualTo(SpecMilestone.FULU);

    final SignedVoluntaryExit exit =
        new VoluntaryExitGenerator(fuluSpec, fuluStorage.chainBuilder().getValidatorKeys())
            .withEpoch(state, exitEpoch, 3);
    assertThat(fuluSpec.verifyVoluntaryExitSignature(state, exit, BLSSignatureVerifier.SIMPLE))
        .isTrue();
    assertThat(fuluSpec.validateVoluntaryExit(state, exit)).isEmpty();

    final Eth2TopicHandler<SignedVoluntaryExit> fuluHandler =
        new VoluntaryExitGossipManager(
                fuluChainData,
                asyncRunner,
                gossipNetwork,
                gossipEncoding,
                fuluChainData.getForkInfo(fuluEpoch).orElseThrow(),
                fuluChainData.getForkDigest(fuluEpoch),
                processor,
                fuluSpec.getNetworkingConfig(),
                DebugDataDumper.NOOP)
            .getTopicHandler();
    when(processor.process(exit, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        fuluHandler.handleMessage(fuluHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    verify(processor).process(exit, Optional.empty());
  }

  @Test
  public void handleMessage_exitFromEarlierFork_rejectedByProcessor() {
    final SignedVoluntaryExit exit =
        exitGenerator.withEpoch(
            getBestState(), spec.computeEpochAtSlot(wrongForkSlot).intValue(), 3);
    when(processor.process(exit, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Invalid exit")));
    Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verify(processor).process(exit, Optional.empty());
  }

  @Test
  public void handleMessage_ignoredExit() {
    final SignedVoluntaryExit exit = exitGenerator.withEpoch(getBestState(), validEpoch, 3);
    when(processor.process(exit, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    assertThat(topicHandler.getTopic())
        .isEqualTo("/eth2/" + forkDigest.toUnprefixedHexString() + "/voluntary_exit/ssz_snappy");
  }

  private BeaconState getBestState() {
    return safeJoin(recentChainData.getBestState().orElseThrow());
  }
}
