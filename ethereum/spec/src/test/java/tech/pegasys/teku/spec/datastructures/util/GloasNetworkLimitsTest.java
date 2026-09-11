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

package tech.pegasys.teku.spec.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsGloas;
import tech.pegasys.teku.spec.datastructures.util.GloasNetworkLimits.LimitViolation;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class GloasNetworkLimitsTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = UInt64.ONE;
  private final SpecConfigGloas config =
      SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
  private BeaconBlockBodySchemaGloas<?> bodySchema;

  @BeforeEach
  void setUp() {
    bodySchema =
        BeaconBlockBodySchemaGloas.required(
            spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema());
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldAcceptBodyAtLimits() {
    final BeaconBlockBodyGloas body =
        body(
            builder ->
                builder
                    .proposerSlashings(
                        list(
                            bodySchema.getProposerSlashingsSchema(),
                            dataStructureUtil::randomProposerSlashing,
                            config.getMaxProposerSlashings()))
                    .attesterSlashings(
                        list(
                            bodySchema.getAttesterSlashingsSchema(),
                            dataStructureUtil::randomAttesterSlashing,
                            config.getMaxAttesterSlashingsElectra()))
                    .attestations(
                        dataStructureUtil.randomAttestations(
                            config.getMaxAttestationsElectra(), slot))
                    .voluntaryExits(
                        list(
                            bodySchema.getVoluntaryExitsSchema(),
                            dataStructureUtil::randomSignedVoluntaryExit,
                            config.getMaxVoluntaryExits()))
                    .blsToExecutionChanges(
                        list(
                            bodySchema.getBlsToExecutionChangesSchema(),
                            dataStructureUtil::randomSignedBlsToExecutionChange,
                            config.getMaxBlsToExecutionChanges()))
                    .payloadAttestations(
                        list(
                            bodySchema.getPayloadAttestationsSchema(),
                            dataStructureUtil::randomPayloadAttestation,
                            config.getMaxPayloadAttestations())));

    assertThat(GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config)).isEmpty();
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldRejectTooManyProposerSlashings() {
    final int limit = config.getMaxProposerSlashings();
    final BeaconBlockBodyGloas body =
        body(
            builder ->
                builder.proposerSlashings(
                    list(
                        bodySchema.getProposerSlashingsSchema(),
                        dataStructureUtil::randomProposerSlashing,
                        limit + 1)));

    assertThat(GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config))
        .contains(new LimitViolation("Block", "proposer slashings", limit + 1, limit));
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldRejectTooManyAttesterSlashings() {
    final int limit = config.getMaxAttesterSlashingsElectra();
    final BeaconBlockBodyGloas body =
        body(
            builder ->
                builder.attesterSlashings(
                    list(
                        bodySchema.getAttesterSlashingsSchema(),
                        dataStructureUtil::randomAttesterSlashing,
                        limit + 1)));

    assertThat(GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config))
        .contains(new LimitViolation("Block", "attester slashings", limit + 1, limit));
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldRejectTooManyAttestations() {
    final int limit = config.getMaxAttestationsElectra();
    final BeaconBlockBodyGloas body =
        body(
            builder -> builder.attestations(dataStructureUtil.randomAttestations(limit + 1, slot)));

    assertThat(GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config))
        .contains(new LimitViolation("Block", "attestations", limit + 1, limit));
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldRejectAnyDeposit() {
    final BeaconBlockBodyGloas body =
        body(
            builder ->
                builder.deposits(
                    list(bodySchema.getDepositsSchema(), dataStructureUtil::randomDeposit, 1)));

    final Optional<LimitViolation> violation =
        GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config);
    assertThat(violation).contains(new LimitViolation("Block", "deposits", 1, 0));
    assertThat(violation.orElseThrow().describe())
        .isEqualTo("Block must not contain deposits, found 1");
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldRejectTooManyVoluntaryExits() {
    final int limit = config.getMaxVoluntaryExits();
    final BeaconBlockBodyGloas body =
        body(
            builder ->
                builder.voluntaryExits(
                    list(
                        bodySchema.getVoluntaryExitsSchema(),
                        dataStructureUtil::randomSignedVoluntaryExit,
                        limit + 1)));

    assertThat(GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config))
        .contains(new LimitViolation("Block", "voluntary exits", limit + 1, limit));
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldRejectTooManyBlsToExecutionChanges() {
    final int limit = config.getMaxBlsToExecutionChanges();
    final BeaconBlockBodyGloas body =
        body(
            builder ->
                builder.blsToExecutionChanges(
                    list(
                        bodySchema.getBlsToExecutionChangesSchema(),
                        dataStructureUtil::randomSignedBlsToExecutionChange,
                        limit + 1)));

    assertThat(GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config))
        .contains(new LimitViolation("Block", "bls to execution changes", limit + 1, limit));
  }

  @Test
  void verifyBlockBodyOperationLimits_shouldRejectTooManyPayloadAttestations() {
    final int limit = config.getMaxPayloadAttestations();
    final BeaconBlockBodyGloas body =
        body(
            builder ->
                builder.payloadAttestations(
                    list(
                        bodySchema.getPayloadAttestationsSchema(),
                        dataStructureUtil::randomPayloadAttestation,
                        limit + 1)));

    final Optional<LimitViolation> violation =
        GloasNetworkLimits.verifyBlockBodyOperationLimits(body, config);
    assertThat(violation)
        .contains(new LimitViolation("Block", "payload attestations", limit + 1, limit));
    assertThat(violation.orElseThrow().describe())
        .isEqualTo(
            String.format("Block has %d payload attestations, max allowed %d", limit + 1, limit));
  }

  @Test
  void verifyExecutionRequestsLimits_shouldAcceptRequestsAtLimits() {
    final ExecutionRequestsGloas requests =
        requests(
            builder ->
                builder
                    .withdrawals(
                        items(
                            dataStructureUtil::randomWithdrawalRequest,
                            config.getMaxWithdrawalRequestsPerPayload()))
                    .consolidations(
                        items(
                            dataStructureUtil::randomConsolidationRequest,
                            config.getMaxConsolidationRequestsPerPayload()))
                    .builderDeposits(
                        () ->
                            items(
                                dataStructureUtil::randomBuilderDepositRequest,
                                config.getMaxBuilderDepositRequestsPerPayload()))
                    .builderExits(
                        () ->
                            items(
                                dataStructureUtil::randomBuilderExitRequest,
                                config.getMaxBuilderExitRequestsPerPayload())));

    assertThat(GloasNetworkLimits.verifyExecutionRequestsLimits("Subject", requests, config))
        .isEmpty();
  }

  @Test
  void verifyExecutionRequestsLimits_shouldRejectTooManyWithdrawalRequests() {
    final int limit = config.getMaxWithdrawalRequestsPerPayload();
    final ExecutionRequestsGloas requests =
        requests(
            builder ->
                builder.withdrawals(items(dataStructureUtil::randomWithdrawalRequest, limit + 1)));

    assertThat(GloasNetworkLimits.verifyExecutionRequestsLimits("Subject", requests, config))
        .contains(new LimitViolation("Subject", "withdrawal requests", limit + 1, limit));
  }

  @Test
  void verifyExecutionRequestsLimits_shouldRejectTooManyConsolidationRequests() {
    final int limit = config.getMaxConsolidationRequestsPerPayload();
    final ExecutionRequestsGloas requests =
        requests(
            builder ->
                builder.consolidations(
                    items(dataStructureUtil::randomConsolidationRequest, limit + 1)));

    assertThat(GloasNetworkLimits.verifyExecutionRequestsLimits("Subject", requests, config))
        .contains(new LimitViolation("Subject", "consolidation requests", limit + 1, limit));
  }

  @Test
  void verifyExecutionRequestsLimits_shouldRejectTooManyBuilderDepositRequests() {
    final int limit = config.getMaxBuilderDepositRequestsPerPayload();
    final ExecutionRequestsGloas requests =
        requests(
            builder ->
                builder.builderDeposits(
                    () -> items(dataStructureUtil::randomBuilderDepositRequest, limit + 1)));

    assertThat(GloasNetworkLimits.verifyExecutionRequestsLimits("Subject", requests, config))
        .contains(new LimitViolation("Subject", "builder deposit requests", limit + 1, limit));
  }

  @Test
  void verifyExecutionRequestsLimits_shouldRejectTooManyBuilderExitRequests() {
    final int limit = config.getMaxBuilderExitRequestsPerPayload();
    final ExecutionRequestsGloas requests =
        requests(
            builder ->
                builder.builderExits(
                    () -> items(dataStructureUtil::randomBuilderExitRequest, limit + 1)));

    assertThat(GloasNetworkLimits.verifyExecutionRequestsLimits("Subject", requests, config))
        .contains(new LimitViolation("Subject", "builder exit requests", limit + 1, limit));
  }

  @Test
  void verifyWithdrawalsLimit_shouldAcceptPayloadAtLimit() {
    final int limit = config.getMaxWithdrawalsPerPayload();
    final ExecutionPayload payload =
        dataStructureUtil.randomExecutionPayload(
            slot,
            builder ->
                builder.withdrawals(() -> items(dataStructureUtil::randomWithdrawal, limit)));

    assertThat(GloasNetworkLimits.verifyWithdrawalsLimit("Subject", payload, config)).isEmpty();
  }

  @Test
  void verifyWithdrawalsLimit_shouldRejectTooManyWithdrawals() {
    final int limit = config.getMaxWithdrawalsPerPayload();
    final ExecutionPayload payload =
        dataStructureUtil.randomExecutionPayload(
            slot,
            builder ->
                builder.withdrawals(() -> items(dataStructureUtil::randomWithdrawal, limit + 1)));

    assertThat(GloasNetworkLimits.verifyWithdrawalsLimit("Subject", payload, config))
        .contains(new LimitViolation("Subject", "withdrawals", limit + 1, limit));
  }

  private BeaconBlockBodyGloas body(final Consumer<BeaconBlockBodyBuilder> modifier) {
    return BeaconBlockBodyGloas.required(
        dataStructureUtil.randomBeaconBlockBody(
            slot,
            builder -> {
              // the random body carries one deposit, which Gloas forbids
              builder.deposits(bodySchema.getDepositsSchema().getDefault());
              modifier.accept(builder);
            }));
  }

  private ExecutionRequestsGloas requests(final Consumer<ExecutionRequestsBuilder> modifier) {
    final ExecutionRequestsBuilder builder = dataStructureUtil.randomExecutionRequestsBuilder(slot);
    modifier.accept(builder);
    return ExecutionRequestsGloas.required(builder.build());
  }

  private <T extends SszData> SszList<T> list(
      final SszListSchema<T, ?> schema, final Supplier<T> generator, final int count) {
    return dataStructureUtil.randomSszList(schema, generator, count);
  }

  private <T> List<T> items(final Supplier<T> generator, final int count) {
    return IntStream.range(0, count).mapToObj(__ -> generator.get()).toList();
  }
}
