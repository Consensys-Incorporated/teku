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

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.ProposerSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ProposerSlashingTopicHandlerTest extends AbstractTopicHandlerTest<ProposerSlashing> {

  @Override
  protected Eth2TopicHandler<ProposerSlashing> createHandler() {
    return new ProposerSlashingGossipManager(
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

  @Test
  public void handleMessage_validSlashing() {
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(validSlot, UInt64.ZERO);
    when(processor.process(slashing, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_validSlashingForOffenceInPreviousFork() {
    // The head is in Bellatrix, the double proposal happened in the last Altair slot
    final UInt64 offendingSlot = validSlot.minus(1);
    final SignedBlockAndState offendingBlock = chainBuilder.getBlockAndStateAtSlot(offendingSlot);
    final ProposerSlashing slashing =
        chainBuilder.createProposerSlashingForAttestation(offendingBlock);
    assertThat(slashing.getHeader1().getMessage().getSlot()).isEqualTo(offendingSlot);
    assertThat(spec.atSlot(offendingSlot).getMilestone()).isEqualTo(SpecMilestone.ALTAIR);

    final BeaconState headState = getChainHead().getState();
    assertThat(spec.atSlot(headState.getSlot()).getMilestone()).isEqualTo(SpecMilestone.BELLATRIX);
    assertThat(spec.validateProposerSlashing(headState, slashing)).isEmpty();
    assertThat(
            spec.verifyProposerSlashingSignature(headState, slashing, BLSSignatureVerifier.SIMPLE))
        .isTrue();

    when(processor.process(slashing, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    verify(processor).process(slashing, Optional.empty());
  }

  @Test
  public void handleMessage_slashingForOffenceInOlderFork_leftToProcessor() {
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(wrongForkSlot, UInt64.ZERO);
    when(processor.process(slashing, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(processor).process(slashing, Optional.empty());
  }

  @Test
  public void handleMessage_ignoredSlashing() {
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(validSlot, UInt64.ZERO);
    when(processor.process(slashing, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_rejectedSlashing() {
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(validSlot, UInt64.ZERO);
    when(processor.process(slashing, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Nope")));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
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
        .isEqualTo("/eth2/" + forkDigest.toUnprefixedHexString() + "/proposer_slashing/ssz_snappy");
  }
}
