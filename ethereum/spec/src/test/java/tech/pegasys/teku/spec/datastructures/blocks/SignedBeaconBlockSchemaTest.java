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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsFields;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Gloas block bodies use progressive lists, so the consensus count limits are enforced by the
 * schemas (spec {@code verify_block_body_operation_limits} and {@code
 * verify_execution_requests_limits}, which MAY be performed when deserializing).
 */
class SignedBeaconBlockSchemaTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalGloas();
  private static final DataStructureUtil DATA_STRUCTURE_UTIL = new DataStructureUtil(SPEC);
  private static final UInt64 SLOT = UInt64.ONE;
  private static final SpecConfigGloas CONFIG =
      SpecConfigGloas.required(SPEC.forMilestone(SpecMilestone.GLOAS).getConfig());

  private final SignedBeaconBlockSchema schema =
      SPEC.atSlot(SLOT).getSchemaDefinitions().getSignedBeaconBlockSchema();

  @Test
  void sszDeserialize_shouldRoundTripValidGloasBlock() {
    final SignedBeaconBlock block = DATA_STRUCTURE_UTIL.randomSignedBeaconBlock(SLOT);
    assertThat(schema.sszDeserialize(block.sszSerialize())).isEqualTo(block);
  }

  static Stream<Arguments> bodyOperationLimits() {
    return Stream.of(
        Arguments.of(
            BlockBodyFields.PROPOSER_SLASHINGS,
            CONFIG.getMaxProposerSlashings(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomProposerSlashing),
        Arguments.of(
            BlockBodyFields.ATTESTER_SLASHINGS,
            CONFIG.getMaxAttesterSlashingsElectra(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomAttesterSlashing),
        Arguments.of(
            BlockBodyFields.ATTESTATIONS,
            CONFIG.getMaxAttestationsElectra(),
            (Supplier<SszData>) () -> DATA_STRUCTURE_UTIL.randomAttestation(SLOT)),
        Arguments.of(
            BlockBodyFields.DEPOSITS, 0, (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomDeposit),
        Arguments.of(
            BlockBodyFields.VOLUNTARY_EXITS,
            CONFIG.getMaxVoluntaryExits(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomSignedVoluntaryExit),
        Arguments.of(
            BlockBodyFields.BLS_TO_EXECUTION_CHANGES,
            CONFIG.getMaxBlsToExecutionChanges(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomSignedBlsToExecutionChange),
        Arguments.of(
            BlockBodyFields.PAYLOAD_ATTESTATIONS,
            CONFIG.getMaxPayloadAttestations(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomPayloadAttestation));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bodyOperationLimits")
  void sszDeserialize_shouldRejectBodyOperationListAboveLimit(
      final SszFieldName field, final int limit, final Supplier<SszData> generator) {
    final BeaconBlockBody body =
        DATA_STRUCTURE_UTIL.withOversizedProgressiveListField(
            DATA_STRUCTURE_UTIL.randomBeaconBlockBody(SLOT), field, generator, limit + 1);

    assertThatThrownBy(() -> schema.sszDeserialize(blockWithBody(body).sszSerialize()))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage("List length %d exceeds max length %d", limit + 1, limit);
  }

  static Stream<Arguments> parentExecutionRequestsLimits() {
    return Stream.of(
        Arguments.of(
            ExecutionRequestsFields.WITHDRAWALS,
            CONFIG.getMaxWithdrawalRequestsPerPayload(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomWithdrawalRequest),
        Arguments.of(
            ExecutionRequestsFields.CONSOLIDATIONS,
            CONFIG.getMaxConsolidationRequestsPerPayload(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomConsolidationRequest),
        Arguments.of(
            ExecutionRequestsFieldsGloas.BUILDER_DEPOSITS,
            CONFIG.getMaxBuilderDepositRequestsPerPayload(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomBuilderDepositRequest),
        Arguments.of(
            ExecutionRequestsFieldsGloas.BUILDER_EXITS,
            CONFIG.getMaxBuilderExitRequestsPerPayload(),
            (Supplier<SszData>) DATA_STRUCTURE_UTIL::randomBuilderExitRequest));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parentExecutionRequestsLimits")
  void sszDeserialize_shouldRejectParentExecutionRequestListAboveLimit(
      final SszFieldName field, final int limit, final Supplier<SszData> generator) {
    final ExecutionRequests requests =
        DATA_STRUCTURE_UTIL.withOversizedProgressiveListField(
            DATA_STRUCTURE_UTIL.randomExecutionRequests(SLOT), field, generator, limit + 1);
    final BeaconBlockBody body =
        DATA_STRUCTURE_UTIL.randomBeaconBlockBody(
            SLOT, builder -> builder.parentExecutionRequests(requests));

    assertThatThrownBy(() -> schema.sszDeserialize(blockWithBody(body).sszSerialize()))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage("List length %d exceeds max length %d", limit + 1, limit);
  }

  @Test
  void sszDeserialize_shouldRejectTooManyDepositRequestsOnlyByPayloadSize() {
    // deposit requests have no Gloas limit: an oversized list is only bounded by MAX_PAYLOAD_SIZE
    final ExecutionRequests requests =
        DATA_STRUCTURE_UTIL
            .randomExecutionRequestsBuilder(SLOT)
            .deposits(
                Stream.generate(DATA_STRUCTURE_UTIL::randomDepositRequest)
                    .limit(CONFIG.getMaxDepositRequestsPerPayload() + 1)
                    .toList())
            .build();
    final BeaconBlockBody body =
        DATA_STRUCTURE_UTIL.randomBeaconBlockBody(
            SLOT, builder -> builder.parentExecutionRequests(requests));
    final Bytes ssz = blockWithBody(body).sszSerialize();

    assertThat(schema.sszDeserialize(ssz).sszSerialize()).isEqualTo(ssz);
  }

  private SignedBeaconBlock blockWithBody(final BeaconBlockBody body) {
    return schema.create(
        DATA_STRUCTURE_UTIL.randomBeaconBlock(SLOT, body), DATA_STRUCTURE_UTIL.randomSignature());
  }
}
