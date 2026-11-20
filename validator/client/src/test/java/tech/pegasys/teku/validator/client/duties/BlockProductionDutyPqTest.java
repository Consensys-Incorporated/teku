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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.BuilderConfigProvider;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.execution.ExecutionPayloadBidEventsChannel;
import tech.pegasys.teku.validator.client.signer.MilestoneBasedBlockContainerSigner;
import tech.pegasys.teku.xmss.XmssSignature;

/** From PQ, a proposer reveals its VRF proof and signs its block with leanXMSS. */
class BlockProductionDutyPqTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = UInt64.valueOf(42);
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final XmssSigner xmssSigner = mock(XmssSigner.class);
  private final Signer signer = mock(Signer.class);
  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          signer,
          Optional::empty,
          false,
          Optional.of(xmssSigner));
  private final BuilderConfigProvider builderConfigProvider = mock(BuilderConfigProvider.class);

  private final BlockProductionDuty duty =
      new BlockProductionDuty(
          validator,
          slot,
          forkProvider,
          validatorApiChannel,
          new MilestoneBasedBlockContainerSigner(spec),
          spec,
          ValidatorDutyMetrics.create(new StubMetricsSystem()),
          mock(ExecutionPayloadBidEventsChannel.class),
          builderConfigProvider);

  @Test
  void shouldRequestBlockWithVrfRevealAndSignItWithXmss() {
    final List<Bytes32> vrfProof =
        List.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32());
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(slot);
    final BeaconBlock block = blockContainerAndMetaData.blockContainer().getBlock();
    final XmssSignature signature =
        XmssSignature.fromBytes(dataStructureUtil.randomXmssSignature().getBytes());
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(builderConfigProvider.getBuilderConfig(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(xmssSigner.computeVrfProof(slot)).thenReturn(completedFuture(vrfProof));
    when(validatorApiChannel.createUnsignedBlockWithVrfReveal(
            eq(slot), eq(vrfProof), any(), eq(false), any()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(xmssSigner.signBlock(block, fork)).thenReturn(completedFuture(signature));
    when(validatorApiChannel.sendSignedBlock(any(), any(), any()))
        .thenReturn(completedFuture(SendSignedBlockResult.success(block.getRoot())));

    final SafeFuture<DutyResult> result = duty.performDuty();

    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getSuccessCount()).isEqualTo(1);
    final SignedBeaconBlock expected =
        spec.atSlot(slot)
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema()
            .create(block, new SszXmssSignature(signature.toBytes()));
    verify(validatorApiChannel).sendSignedBlock(eq(expected), any(), any());
    verify(signer, never()).createRandaoReveal(any(), any());
  }
}
