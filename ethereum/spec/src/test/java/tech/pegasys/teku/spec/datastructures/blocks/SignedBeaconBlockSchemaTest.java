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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszNetworkValidator;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SignedBeaconBlockSchemaTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = UInt64.ONE;
  private final SpecConfigGloas config =
      SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
  private final SignedBeaconBlockSchema schema =
      spec.atSlot(slot).getSchemaDefinitions().getSignedBeaconBlockSchema();

  @Test
  void getNetworkSszValidator_shouldBeEmptyBeforeGloas() {
    final Spec electraSpec = TestSpecFactory.createMinimalElectra();
    assertThat(
            electraSpec
                .getGenesisSchemaDefinitions()
                .getSignedBeaconBlockSchema()
                .getNetworkSszValidator())
        .isEmpty();
    assertThat(
            electraSpec
                .getGenesisSchemaDefinitions()
                .getSignedBlindedBeaconBlockSchema()
                .getNetworkSszValidator())
        .isEmpty();
  }

  @Test
  void getNetworkSszValidator_shouldAcceptValidGloasBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
    assertThatNoException().isThrownBy(() -> validator().validate(block));
  }

  @Test
  void getNetworkSszValidator_shouldRejectTooManyAttestations() {
    final int limit = config.getMaxAttestationsElectra();
    final SignedBeaconBlock block =
        blockWithBody(
            dataStructureUtil.randomBeaconBlockBody(
                slot,
                builder ->
                    builder.attestations(dataStructureUtil.randomAttestations(limit + 1, slot))));

    assertThatThrownBy(() -> validator().validate(block))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage("Block has %d attestations, max allowed %d", limit + 1, limit);
  }

  @Test
  void getNetworkSszValidator_shouldRejectTooManyParentWithdrawalRequests() {
    final int limit = config.getMaxWithdrawalRequestsPerPayload();
    final List<WithdrawalRequest> tooManyWithdrawalRequests =
        IntStream.range(0, limit + 1)
            .mapToObj(__ -> dataStructureUtil.randomWithdrawalRequest())
            .toList();
    final ExecutionRequests parentExecutionRequests =
        dataStructureUtil
            .randomExecutionRequestsBuilder(slot)
            .withdrawals(tooManyWithdrawalRequests)
            .build();
    final SignedBeaconBlock block =
        blockWithBody(
            dataStructureUtil.randomBeaconBlockBody(
                slot, builder -> builder.parentExecutionRequests(parentExecutionRequests)));

    assertThatThrownBy(() -> validator().validate(block))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage(
            "Parent execution requests has %d withdrawal requests, max allowed %d",
            limit + 1, limit);
  }

  private SszNetworkValidator<SignedBeaconBlock> validator() {
    return schema.getNetworkSszValidator().orElseThrow();
  }

  private SignedBeaconBlock blockWithBody(final BeaconBlockBody body) {
    return schema.create(
        dataStructureUtil.randomBeaconBlock(slot, body), dataStructureUtil.randomSignature());
  }
}
