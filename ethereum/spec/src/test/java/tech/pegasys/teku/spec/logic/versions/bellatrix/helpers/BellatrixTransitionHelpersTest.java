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

package tech.pegasys.teku.spec.logic.versions.bellatrix.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BellatrixTransitionHelpersTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = dataStructureUtil.randomUInt64();
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

  @Test
  void shouldBeInvalidForTTDNotSupported() {
    assertThat(validateMergeBlock())
        .isCompletedWithValue(
            PayloadStatus.invalid(
                Optional.empty(), Optional.of("Total difficulty check is no more supported")));
  }

  @Test
  void shouldBeValidWhenTerminalBlockHashMatchesInActivationEpoch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(payload.getParentHash(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch);
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.VALID);
  }

  @Test
  void shouldBeValidWhenTerminalBlockHashMatchesAfterActivationEpoch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(payload.getParentHash(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch.plus(1));
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.VALID);
  }

  @Test
  void shouldBeInvalidWhenTerminalBlockHashDoesNotMatch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(dataStructureUtil.randomBytes32(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch);
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.INVALID);
  }

  @Test
  void shouldBeInvalidWhenBeforeActivationEpoch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(payload.getParentHash(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch).minus(1);
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.INVALID);
  }

  private Spec createSpec(
      final Bytes32 terminalBlockHash, final UInt64 terminalBlockHashActivationEpoch) {
    return TestSpecFactory.createMinimalBellatrix(
        c ->
            c.bellatrixBuilder(
                b ->
                    b.terminalBlockHash(terminalBlockHash)
                        .terminalBlockHashActivationEpoch(terminalBlockHashActivationEpoch)));
  }

  private SafeFuture<PayloadStatus> validateMergeBlock() {
    return spec.getGenesisSpec()
        .getBellatrixTransitionHelpers()
        .orElseThrow()
        .validateMergeBlock(payload, slot);
  }

  private void assertPayloadResultStatus(
      final SafeFuture<PayloadStatus> result, final ExecutionPayloadStatus expectedStatus) {
    assertThat(result)
        .isCompletedWithValueMatching(
            payloadResult -> payloadResult.hasStatus(expectedStatus),
            "a payload result with status " + expectedStatus);
  }
}
