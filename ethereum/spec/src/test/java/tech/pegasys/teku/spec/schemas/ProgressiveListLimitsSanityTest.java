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

package tech.pegasys.teku.spec.schemas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsFields;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas;

/**
 * Every progressive list of a beacon block body or of execution requests declares the limit the
 * spec assigns to it (the {@code LIMIT} of the corresponding {@code ProgressiveList} type),
 * enforced by the schema on construction and deserialization. A few lists are deliberately
 * unlimited ({@code LIMIT = None}) and are asserted as {@link #NO_LIMIT} so that they stay
 * accounted for.
 *
 * <p>Each container is also checked to have no list field beyond the ones listed here, so that a
 * list added by a later milestone can't silently arrive without a declared limit.
 */
@TestSpecContext(milestone = {SpecMilestone.GLOAS, SpecMilestone.HEZE})
class ProgressiveListLimitsSanityTest {

  /**
   * The max length reported by a progressive list schema created without a limit (spec {@code LIMIT
   * = None}); such lists are only bounded by {@code MAX_PAYLOAD_SIZE}.
   */
  private static final long NO_LIMIT = Long.MAX_VALUE;

  private SpecConfigGloas config;
  private SchemaDefinitionsGloas schemaDefinitions;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    config = SpecConfigGloas.required(specContext.getSpec().getGenesisSpecConfig());
    schemaDefinitions = SchemaDefinitionsGloas.required(specContext.getSchemaDefinitions());
  }

  @TestTemplate
  void blockBodyListsDeclareSpecLimits() {
    assertDeclaredLimits(schemaDefinitions.getBeaconBlockBodySchema(), expectedBlockBodyLimits());
  }

  @TestTemplate
  void blockBodyListsCoverEveryListFieldOfTheSchema() {
    assertCoversEveryListField(
        schemaDefinitions.getBeaconBlockBodySchema(), expectedBlockBodyLimits());
  }

  @TestTemplate
  void executionRequestsListsDeclareSpecLimits() {
    assertDeclaredLimits(
        schemaDefinitions.getExecutionRequestsSchema(), expectedExecutionRequestsLimits());
  }

  @TestTemplate
  void executionRequestsListsCoverEveryListFieldOfTheSchema() {
    assertCoversEveryListField(
        schemaDefinitions.getExecutionRequestsSchema(), expectedExecutionRequestsLimits());
  }

  private Map<SszFieldName, Long> expectedBlockBodyLimits() {
    return Map.ofEntries(
        Map.entry(BlockBodyFields.PROPOSER_SLASHINGS, (long) config.getMaxProposerSlashings()),
        Map.entry(
            BlockBodyFields.ATTESTER_SLASHINGS, (long) config.getMaxAttesterSlashingsElectra()),
        Map.entry(BlockBodyFields.ATTESTATIONS, (long) config.getMaxAttestationsElectra()),
        // blocks must not contain deposits from Gloas onwards
        Map.entry(BlockBodyFields.DEPOSITS, 0L),
        Map.entry(BlockBodyFields.VOLUNTARY_EXITS, (long) config.getMaxVoluntaryExits()),
        Map.entry(
            BlockBodyFields.BLS_TO_EXECUTION_CHANGES, (long) config.getMaxBlsToExecutionChanges()),
        Map.entry(BlockBodyFields.PAYLOAD_ATTESTATIONS, (long) config.getMaxPayloadAttestations()));
  }

  private Map<SszFieldName, Long> expectedExecutionRequestsLimits() {
    return Map.ofEntries(
        // deposit requests are not covered by `verify_execution_requests_limits`
        Map.entry(ExecutionRequestsFields.DEPOSITS, NO_LIMIT),
        Map.entry(
            ExecutionRequestsFields.WITHDRAWALS,
            (long) config.getMaxWithdrawalRequestsPerPayload()),
        Map.entry(
            ExecutionRequestsFields.CONSOLIDATIONS,
            (long) config.getMaxConsolidationRequestsPerPayload()),
        Map.entry(
            ExecutionRequestsFieldsGloas.BUILDER_DEPOSITS,
            (long) config.getMaxBuilderDepositRequestsPerPayload()),
        Map.entry(
            ExecutionRequestsFieldsGloas.BUILDER_EXITS,
            (long) config.getMaxBuilderExitRequestsPerPayload()));
  }

  private static void assertDeclaredLimits(
      final SszContainerSchema<?> container, final Map<SszFieldName, Long> expectedLimits) {
    final SoftAssertions softly = new SoftAssertions();
    expectedLimits.forEach(
        (field, expectedLimit) ->
            softly
                .assertThat(listMaxLength(container, field))
                .describedAs(field.getSszFieldName())
                .isEqualTo(expectedLimit));
    softly.assertAll();
  }

  private static void assertCoversEveryListField(
      final SszContainerSchema<?> container, final Map<SszFieldName, Long> expectedLimits) {
    assertThat(listFieldNames(container))
        .containsExactlyInAnyOrderElementsOf(
            expectedLimits.keySet().stream().map(SszFieldName::getSszFieldName).toList());
  }

  private static long listMaxLength(
      final SszContainerSchema<?> container, final SszFieldName field) {
    return ((SszListSchema<?, ?>) container.getChildSchema(container.getFieldIndex(field)))
        .getMaxLength();
  }

  private static List<String> listFieldNames(final SszContainerSchema<?> container) {
    final List<String> fieldNames = container.getFieldNames();
    return IntStream.range(0, fieldNames.size())
        .filter(index -> container.getChildSchema(index) instanceof SszListSchema<?, ?>)
        .mapToObj(fieldNames::get)
        .toList();
  }
}
