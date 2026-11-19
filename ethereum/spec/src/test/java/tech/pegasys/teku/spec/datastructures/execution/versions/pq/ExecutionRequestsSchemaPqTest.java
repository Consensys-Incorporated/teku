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

package tech.pegasys.teku.spec.datastructures.execution.versions.pq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionRequestsSchemaPqTest {

  // hash_tree_root(ExecutionRequests()) in the PQ genesis of the ethpandaops genesis generator
  private static final Bytes32 EMPTY_REQUESTS_ROOT =
      Bytes32.fromHexString("0xe885a70cc00b03dfd76e668fa83388f9ebfa4914229e02c9c43c19356c1dd6a7");

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigPq specConfig =
      SpecConfigPq.required(spec.forMilestone(SpecMilestone.PQ).getConfig());
  private final ExecutionRequestsSchemaPq schema =
      SchemaDefinitionsPq.required(spec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions())
          .getPqExecutionRequestsSchema();

  @Test
  void emptyRequestsMatchAnIndependentImplementation() {
    assertThat(schema.createBuilder().build().hashTreeRoot()).isEqualTo(EMPTY_REQUESTS_ROOT);
  }

  @Test
  void dropsThePqRegistrationsAndAddsPreregistrationsAndExits() {
    assertThat(schema.getFieldsCount()).isEqualTo(8);
    assertThat(schema.getFieldNames())
        .containsExactly(
            "deposits",
            "withdrawals",
            "consolidations",
            "builder_deposits",
            "builder_exits",
            "validator_preregistrations",
            "builder_preregistrations",
            "voluntary_exits");
    assertThatThrownBy(schema::getValidatorPqRegistrationRequestsSchema)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(schema::getBuilderPqRegistrationRequestsSchema)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void roundTripsEveryRequestType() {
    final ExecutionRequestsPq requests = requestsOfEveryType();

    final ExecutionRequests roundTripped = schema.sszDeserialize(requests.sszSerialize());

    assertThat(roundTripped).isEqualTo(requests);
    assertThat(roundTripped.getValidatorPreregistrations())
        .isEqualTo(requests.getValidatorPreregistrations());
    assertThat(roundTripped.getBuilderPreregistrations())
        .isEqualTo(requests.getBuilderPreregistrations());
    assertThat(roundTripped.getVoluntaryExits()).isEqualTo(requests.getVoluntaryExits());
    assertThat(roundTripped.getValidatorPqRegistrations()).isEmpty();
    assertThat(roundTripped.toVersionPq()).isPresent();
  }

  @Test
  void limitsTheRequestsPerPayload() {
    final ExecutionRequestsPq requests =
        schema.create(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Collections.nCopies(
                specConfig.getMaxValidatorPreregistrationRequestsPerPayload(),
                validatorPreregistration(1)),
            Collections.nCopies(
                specConfig.getMaxBuilderPreregistrationRequestsPerPayload(),
                builderPreregistration(2)),
            Collections.nCopies(
                specConfig.getMaxVoluntaryExitRequestsPerPayload(), voluntaryExit(3)));
    assertThat(schema.sszDeserialize(requests.sszSerialize())).isEqualTo(requests);

    assertThatThrownBy(
            () ->
                schema
                    .getVoluntaryExitRequestsSchema()
                    .createFromElements(
                        Collections.nCopies(
                            specConfig.getMaxVoluntaryExitRequestsPerPayload() + 1,
                            voluntaryExit(3))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convertsGloasRequestsWithoutLosingAny() {
    final ExecutionRequests gloasRequests =
        new DataStructureUtil(TestSpecFactory.createMinimalGloas()).randomExecutionRequests();
    final ExecutionRequests converted = schema.convertFrom(gloasRequests);
    assertThat(converted.getDeposits()).isEqualTo(gloasRequests.getDeposits());
    assertThat(converted.getBuilderExits()).isEqualTo(gloasRequests.getBuilderExits());
    assertThat(converted.toVersionPq()).isPresent();
  }

  @Test
  void requestContainersHaveTheSpecLayout() {
    assertThat(ValidatorPreregistrationRequest.SSZ_SCHEMA.getSszFixedPartSize()).isEqualTo(96);
    assertThat(BuilderPreregistrationRequest.SSZ_SCHEMA.getSszFixedPartSize()).isEqualTo(64);
    assertThat(VoluntaryExitRequest.SSZ_SCHEMA.getSszFixedPartSize()).isEqualTo(32);
    assertThat(validatorPreregistration(1).getVrfRoot()).isEqualTo(Bytes32.repeat((byte) 3));
    assertThat(builderPreregistration(1).getWithdrawalCredentials())
        .isEqualTo(Bytes32.repeat((byte) 2));
    assertThat(voluntaryExit(1).getXmssPublicKey()).isEqualTo(Bytes32.repeat((byte) 1));
  }

  ExecutionRequestsPq requestsOfEveryType() {
    final ExecutionRequests base = dataStructureUtil.randomExecutionRequests();
    return schema.create(
        base.getDeposits(),
        base.getWithdrawals(),
        base.getConsolidations(),
        base.getBuilderDeposits(),
        base.getBuilderExits(),
        List.of(validatorPreregistration(1), validatorPreregistration(4)),
        List.of(builderPreregistration(2)),
        List.of(voluntaryExit(3), voluntaryExit(5)));
  }

  static ValidatorPreregistrationRequest validatorPreregistration(final int seed) {
    return new ValidatorPreregistrationRequest(
        Bytes32.repeat((byte) seed),
        Bytes32.repeat((byte) (seed + 1)),
        Bytes32.repeat((byte) (seed + 2)));
  }

  static BuilderPreregistrationRequest builderPreregistration(final int seed) {
    return new BuilderPreregistrationRequest(
        Bytes32.repeat((byte) seed), Bytes32.repeat((byte) (seed + 1)));
  }

  static VoluntaryExitRequest voluntaryExit(final int seed) {
    return new VoluntaryExitRequest(Bytes32.repeat((byte) seed));
  }
}
