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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip8483;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip8483;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionRequestsEip8483Test {

  private final Spec spec = TestSpecFactory.createMinimalEip8483();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitionsEip8483 schemaDefinitions =
      SchemaDefinitionsEip8483.required(
          spec.forMilestone(SpecMilestone.EIP8483).getSchemaDefinitions());
  private final ExecutionRequestsSchema<?> executionRequestsSchema =
      schemaDefinitions.getExecutionRequestsSchema();
  private final ExecutionRequestsDataCodec codec =
      new ExecutionRequestsDataCodec(executionRequestsSchema);

  private final ValidatorPqRegistrationRequest validatorRequest =
      schemaDefinitions
          .getValidatorPqRegistrationRequestSchema()
          .create(
              new Bytes20(Bytes.repeat((byte) 0x11, 20)),
              dataStructureUtil.randomPublicKey(),
              Bytes.repeat((byte) 0x22, 64));
  private final BuilderPqRegistrationRequest builderRequest =
      schemaDefinitions
          .getBuilderPqRegistrationRequestSchema()
          .create(
              new Bytes20(Bytes.repeat((byte) 0x33, 20)),
              dataStructureUtil.randomPublicKey(),
              Bytes32.repeat((byte) 0x44));

  @Test
  void requestSszSizesMatchPredeployRecords() {
    // validator predeploy returns 132-byte records, builder predeploy 100-byte records
    assertThat(validatorRequest.sszSerialize().size()).isEqualTo(132);
    assertThat(builderRequest.sszSerialize().size()).isEqualTo(100);
  }

  @Test
  void schemaIsEip8483WithSevenFields() {
    assertThat(executionRequestsSchema).isInstanceOf(ExecutionRequestsSchemaEip8483.class);
    assertThat(executionRequestsSchema.getFieldsCount()).isEqualTo(7);
  }

  @Test
  void emptyRootDiffersFromGloas() {
    // ExecutionRequests is a ProgressiveContainer, so adding fields changes the root even when
    // the extra lists are empty. This is the fork-boundary issue noted in eip8483/fork.md.
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ExecutionRequests gloasEmpty =
        gloasSpec
            .getGenesisSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getExecutionRequestsSchema()
            .getDefault();
    final ExecutionRequests eip8483Empty = executionRequestsSchema.getDefault();
    assertThat(eip8483Empty.hashTreeRoot()).isNotEqualTo(gloasEmpty.hashTreeRoot());
  }

  @Test
  void codecRoundTripWithPqRequests() {
    final ExecutionRequests requests =
        executionRequestsSchema
            .createBuilder()
            .validatorPqRegistrations(() -> List.of(validatorRequest))
            .builderPqRegistrations(() -> List.of(builderRequest))
            .build();

    final List<Bytes> encoded = codec.encode(requests);
    assertThat(encoded).hasSize(2);
    assertThat(encoded.get(0).get(0)).isEqualTo(ValidatorPqRegistrationRequest.REQUEST_TYPE);
    assertThat(encoded.get(1).get(0)).isEqualTo(BuilderPqRegistrationRequest.REQUEST_TYPE);

    final ExecutionRequests decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(requests);
    assertThat(decoded.getValidatorPqRegistrations()).containsExactly(validatorRequest);
    assertThat(decoded.getBuilderPqRegistrations()).containsExactly(builderRequest);
  }

  @Test
  void decodeRejectsTooManyValidatorPqRegistrations() {
    final int max =
        spec.getGenesisSpecConfig()
            .toVersionEip8483()
            .orElseThrow()
            .getMaxValidatorPqRegistrationRequestsPerPayload();
    final Bytes oneRequest = validatorRequest.sszSerialize();
    final Bytes[] tooMany = new Bytes[max + 1];
    Arrays.fill(tooMany, oneRequest);
    final Bytes encoded =
        Bytes.concatenate(
            ValidatorPqRegistrationRequest.REQUEST_TYPE_PREFIX, Bytes.concatenate(tooMany));

    assertThatThrownBy(() -> codec.decode(List.of(encoded))).isInstanceOf(Exception.class);
  }

  @Test
  void gloasCodecIgnoresPqRequestTypes() {
    // Before EIP-8483 the builder NO-OPs unknown-to-the-fork request types (same as Gloas types
    // before Gloas), so a Gloas node does not fail on them.
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ExecutionRequestsDataCodec gloasCodec =
        new ExecutionRequestsDataCodec(
            gloasSpec
                .getGenesisSchemaDefinitions()
                .toVersionElectra()
                .orElseThrow()
                .getExecutionRequestsSchema());
    final Bytes encoded =
        Bytes.concatenate(
            ValidatorPqRegistrationRequest.REQUEST_TYPE_PREFIX, validatorRequest.sszSerialize());
    assertThat(gloasCodec.decode(List.of(encoded)).getValidatorPqRegistrations()).isEmpty();
  }

  @Test
  void convertFromGloasPreservesContentAndChangesType() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ExecutionRequestsSchema<?> gloasSchema =
        gloasSpec
            .getGenesisSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getExecutionRequestsSchema();
    final ExecutionRequests gloasRequests =
        gloasSchema
            .createBuilder()
            .withdrawals(List.of(new DataStructureUtil(gloasSpec).randomWithdrawalRequest()))
            .build();

    final ExecutionRequests converted = executionRequestsSchema.convertFrom(gloasRequests);

    assertThat(converted.getSchema()).isEqualTo(executionRequestsSchema);
    assertThat(converted.getWithdrawals()).isEqualTo(gloasRequests.getWithdrawals());
    assertThat(converted.getValidatorPqRegistrations()).isEmpty();
    // and back again: the Gloas root is recovered, which is what the fork-boundary bridge relies on
    assertThat(
            schemaDefinitions
                .getPreForkExecutionRequestsSchema()
                .convertFrom(converted)
                .hashTreeRoot())
        .isEqualTo(gloasRequests.hashTreeRoot());
  }

  @Test
  void convertToPreForkRejectsPqRegistrations() {
    final ExecutionRequests requests =
        executionRequestsSchema
            .createBuilder()
            .validatorPqRegistrations(() -> List.of(validatorRequest))
            .build();
    assertThatThrownBy(
            () -> schemaDefinitions.getPreForkExecutionRequestsSchema().convertFrom(requests))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
