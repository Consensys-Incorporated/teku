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

package tech.pegasys.teku.spec.logic.versions.eip8483.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.BuilderPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateEip8483;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.MutableBeaconStateEip8483;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.BuilderPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip8483;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/** Mirrors the EIP-8483 block_processing pyspec tests in ethereum/consensus-specs PR 5809. */
class ExecutionRequestsProcessorEip8483Test {

  private static final Bytes20 ADDRESS_11 = address(0x11);
  private static final Bytes20 ADDRESS_44 = address(0x44);

  private final Spec spec = TestSpecFactory.createMinimalEip8483();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitionsEip8483 schemaDefinitions =
      SchemaDefinitionsEip8483.required(spec.getGenesisSchemaDefinitions());
  private final ExecutionRequestsProcessor processor =
      spec.getExecutionRequestsProcessor(UInt64.ZERO);

  private BeaconStateEip8483 state;

  @BeforeEach
  void setUp() {
    state = BeaconStateEip8483.required(dataStructureUtil.stateBuilderGloas(8, 2, 0).build());
  }

  // ---- validator PQ registrations ----

  @Test
  void validator_success() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    processValidator(validatorRequest(0, repeat(0x22, 64)));

    assertThat(state.getValidatorPqRegistrations().asList())
        .containsExactly(validatorRegistration(0, repeat(0x22, 64)));
  }

  @Test
  void validator_compoundingCredentials() {
    setWithdrawalCredentials(
        0,
        Bytes.concatenate(Bytes.of(0x02), Bytes.wrap(new byte[11]), ADDRESS_11.getWrappedBytes()));
    processValidator(validatorRequest(0, repeat(0x22, 64)));

    assertThat(state.getValidatorPqRegistrations().size()).isEqualTo(1);
    assertThat(state.getValidatorPqRegistrations().get(0).getValidatorIndex())
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void validator_overwritesRegistration() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    processValidator(validatorRequest(0, repeat(0x22, 64)));
    processValidator(validatorRequest(0, repeat(0x33, 64)));

    assertThat(state.getValidatorPqRegistrations().asList())
        .containsExactly(validatorRegistration(0, repeat(0x33, 64)));
  }

  @Test
  void validator_registrationsForTwoValidators() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    setExecutionWithdrawalAddress(1, ADDRESS_11);
    processValidator(validatorRequest(1, repeat(0x22, 64)));
    processValidator(validatorRequest(0, repeat(0x33, 64)));

    assertThat(state.getValidatorPqRegistrations().asList())
        .extracting(ValidatorPqRegistration::getValidatorIndex)
        .containsExactly(UInt64.ONE, UInt64.ZERO);
  }

  @Test
  void validator_exitedValidator() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    state =
        BeaconStateEip8483.required(
            state.updated(
                s ->
                    s.getValidators()
                        .update(
                            0,
                            v ->
                                v.withExitEpoch(UInt64.ONE)
                                    .withWithdrawableEpoch(UInt64.valueOf(2)))));
    processValidator(validatorRequest(0, repeat(0x22, 64)));

    assertThat(state.getValidatorPqRegistrations().size()).isEqualTo(1);
  }

  @Test
  void validator_unknownPubkey() {
    final ValidatorPqRegistrationRequest request =
        schemaDefinitions
            .getValidatorPqRegistrationRequestSchema()
            .create(ADDRESS_11, dataStructureUtil.randomPublicKey(), repeat(0x22, 64));
    assertIgnored(() -> processValidator(request));
  }

  @Test
  void validator_blsWithdrawalCredentials() {
    setWithdrawalCredentials(
        0, Bytes.concatenate(Bytes.of(0x00), dataStructureUtil.randomBytes32().slice(1)));
    final ValidatorPqRegistrationRequest request =
        schemaDefinitions
            .getValidatorPqRegistrationRequestSchema()
            .create(ADDRESS_11, validatorPubkey(0), repeat(0x22, 64));
    assertIgnored(() -> processValidator(request));
  }

  @Test
  void validator_blsCredentialsMatchingSourceAddress() {
    // The source address matches, so only the credential prefix check applies
    setWithdrawalCredentials(
        0,
        Bytes.concatenate(Bytes.of(0x00), Bytes.wrap(new byte[11]), ADDRESS_11.getWrappedBytes()));
    assertIgnored(() -> processValidator(validatorRequest(0, repeat(0x22, 64))));
  }

  @Test
  void validator_wrongSourceAddress() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    final ValidatorPqRegistrationRequest request =
        schemaDefinitions
            .getValidatorPqRegistrationRequestSchema()
            .create(ADDRESS_44, validatorPubkey(0), repeat(0x22, 64));
    assertIgnored(() -> processValidator(request));
  }

  @Test
  void validator_pqDataAlreadyRegistered() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    setExecutionWithdrawalAddress(1, ADDRESS_44);
    processValidator(validatorRequest(0, repeat(0x22, 64)));
    assertIgnored(() -> processValidator(validatorRequest(1, repeat(0x22, 64))));
  }

  @Test
  void validator_resubmittedRegistration() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    processValidator(validatorRequest(0, repeat(0x22, 64)));
    assertIgnored(() -> processValidator(validatorRequest(0, repeat(0x22, 64))));
  }

  @Test
  void validator_overwrittenPqDataCanBeRegistered() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    setExecutionWithdrawalAddress(1, ADDRESS_44);
    processValidator(validatorRequest(0, repeat(0x22, 64)));
    processValidator(validatorRequest(0, repeat(0x33, 64)));
    processValidator(validatorRequest(1, repeat(0x22, 64)));

    assertThat(state.getValidatorPqRegistrations().asList())
        .containsExactly(
            validatorRegistration(0, repeat(0x33, 64)), validatorRegistration(1, repeat(0x22, 64)));
  }

  @Test
  void validator_overwrittenPqDataCanBeRegisteredWithinOneBatch() {
    // Same as above, but all three requests arrive in one payload, exercising the batch indexes
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    setExecutionWithdrawalAddress(1, ADDRESS_44);
    final List<ValidatorPqRegistrationRequest> requests =
        List.of(
            validatorRequest(0, repeat(0x22, 64)),
            validatorRequest(0, repeat(0x33, 64)),
            validatorRequest(1, repeat(0x22, 64)),
            // duplicate of validator 1's new data: ignored
            validatorRequest(0, repeat(0x22, 64)));
    state =
        BeaconStateEip8483.required(
            state.updated(s -> processor.processValidatorPqRegistrationRequests(s, requests)));

    assertThat(state.getValidatorPqRegistrations().asList())
        .containsExactly(
            validatorRegistration(0, repeat(0x33, 64)), validatorRegistration(1, repeat(0x22, 64)));
  }

  @Test
  void validator_xmssPubkeyAlreadyRegistered() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    setExecutionWithdrawalAddress(1, ADDRESS_44);
    processValidator(validatorRequest(0, repeat(0x22, 64)));
    // Same XMSS public key, different VRF data
    assertIgnored(
        () ->
            processValidator(
                validatorRequest(1, Bytes.concatenate(repeat(0x22, 32), repeat(0x33, 32)))));
  }

  @Test
  void validator_vrfDataAlreadyRegistered() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    setExecutionWithdrawalAddress(1, ADDRESS_44);
    processValidator(validatorRequest(0, Bytes.concatenate(repeat(0x22, 32), repeat(0x55, 32))));
    // Different XMSS public key, same VRF data
    assertIgnored(
        () ->
            processValidator(
                validatorRequest(1, Bytes.concatenate(repeat(0x33, 32), repeat(0x55, 32)))));
  }

  @Test
  void validator_builderPubkey() {
    final Builder builder = state.getBuilders().get(0);
    final ValidatorPqRegistrationRequest request =
        schemaDefinitions
            .getValidatorPqRegistrationRequestSchema()
            .create(
                new Bytes20(builder.getExecutionAddress().getWrappedBytes()),
                builder.getPublicKey(),
                repeat(0x22, 64));
    assertIgnored(() -> processValidator(request));
  }

  @Test
  void validator_xmssPubkeyRegisteredByBuilder() {
    setExecutionWithdrawalAddress(0, ADDRESS_11);
    appendBuilderRegistration(builderPubkey(0), Bytes32.wrap(repeat(0x22, 32)));
    processValidator(validatorRequest(0, Bytes.concatenate(repeat(0x22, 32), repeat(0x33, 32))));

    assertThat(state.getValidatorPqRegistrations().size()).isEqualTo(1);
  }

  // ---- builder PQ registrations ----

  @Test
  void builder_success() {
    processBuilder(builderRequest(0, repeat32(0x22)));

    assertThat(state.getBuilderPqRegistrations().asList())
        .containsExactly(builderRegistration(builderPubkey(0), repeat32(0x22)));
  }

  @Test
  void builder_overwritesRegistration() {
    processBuilder(builderRequest(0, repeat32(0x22)));
    processBuilder(builderRequest(0, repeat32(0x33)));

    assertThat(state.getBuilderPqRegistrations().asList())
        .containsExactly(builderRegistration(builderPubkey(0), repeat32(0x33)));
  }

  @Test
  void builder_registrationsForTwoBuilders() {
    processBuilder(builderRequest(1, repeat32(0x22)));
    processBuilder(builderRequest(0, repeat32(0x33)));

    assertThat(state.getBuilderPqRegistrations().asList())
        .extracting(BuilderPqRegistration::getBuilderPubkey)
        .containsExactly(builderPubkey(1), builderPubkey(0));
  }

  @Test
  void builder_unknownPubkey() {
    final BuilderPqRegistrationRequest request =
        schemaDefinitions
            .getBuilderPqRegistrationRequestSchema()
            .create(ADDRESS_11, validatorPubkey(0), repeat32(0x22));
    assertIgnored(() -> processBuilder(request));
  }

  @Test
  void builder_wrongSourceAddress() {
    final BuilderPqRegistrationRequest request =
        schemaDefinitions
            .getBuilderPqRegistrationRequestSchema()
            .create(address(0x99), builderPubkey(0), repeat32(0x22));
    assertIgnored(() -> processBuilder(request));
  }

  @Test
  void builder_xmssPubkeyRegisteredByBuilder() {
    processBuilder(builderRequest(0, repeat32(0x22)));
    assertIgnored(() -> processBuilder(builderRequest(1, repeat32(0x22))));
  }

  @Test
  void builder_xmssPubkeyRegisteredByValidator() {
    state =
        BeaconStateEip8483.required(
            state.updated(
                s ->
                    MutableBeaconStateEip8483.required(s)
                        .getValidatorPqRegistrations()
                        .append(
                            validatorRegistration(
                                0, Bytes.concatenate(repeat(0x22, 32), repeat(0x33, 32))))));
    processBuilder(builderRequest(0, repeat32(0x22)));

    assertThat(state.getBuilderPqRegistrations().size()).isEqualTo(1);
  }

  @Test
  void builder_sharesValidatorPubkey() {
    final BLSPublicKey validatorPubkey = validatorPubkey(0);
    state =
        BeaconStateEip8483.required(
            state.updated(
                s -> {
                  final MutableBeaconStateEip8483 mutable = MutableBeaconStateEip8483.required(s);
                  final Builder builder = mutable.getBuilders().get(0);
                  mutable
                      .getBuilders()
                      .set(
                          0,
                          dataStructureUtil
                              .builderBuilder()
                              .publicKey(validatorPubkey)
                              .executionAddress(builder.getExecutionAddress())
                              .balance(builder.getBalance())
                              .depositEpoch(builder.getDepositEpoch())
                              .withdrawableEpoch(builder.getWithdrawableEpoch())
                              .build());
                  mutable
                      .getValidatorPqRegistrations()
                      .append(validatorRegistration(0, repeat(0x22, 64)));
                }));
    processBuilder(builderRequest(0, repeat32(0x33)));

    assertThat(state.getValidatorPqRegistrations().size()).isEqualTo(1);
    assertThat(state.getBuilderPqRegistrations().asList())
        .containsExactly(builderRegistration(validatorPubkey, repeat32(0x33)));
  }

  // ---- helpers ----

  private void processValidator(final ValidatorPqRegistrationRequest request) {
    state =
        BeaconStateEip8483.required(
            state.updated(
                s -> processor.processValidatorPqRegistrationRequests(s, List.of(request))));
  }

  private void processBuilder(final BuilderPqRegistrationRequest request) {
    state =
        BeaconStateEip8483.required(
            state.updated(
                s -> processor.processBuilderPqRegistrationRequests(s, List.of(request))));
  }

  private void assertIgnored(final Runnable action) {
    final BeaconState preState = state;
    action.run();
    assertThat(state.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  private ValidatorPqRegistrationRequest validatorRequest(final int index, final Bytes pqData) {
    final Validator validator = state.getValidators().get(index);
    return schemaDefinitions
        .getValidatorPqRegistrationRequestSchema()
        .create(
            new Bytes20(validator.getWithdrawalCredentials().slice(12)),
            validator.getPublicKey(),
            pqData);
  }

  private BuilderPqRegistrationRequest builderRequest(final int index, final Bytes32 pqData) {
    final Builder builder = state.getBuilders().get(index);
    return schemaDefinitions
        .getBuilderPqRegistrationRequestSchema()
        .create(
            new Bytes20(builder.getExecutionAddress().getWrappedBytes()),
            builder.getPublicKey(),
            pqData);
  }

  private ValidatorPqRegistration validatorRegistration(final int index, final Bytes pqData) {
    return schemaDefinitions
        .getValidatorPqRegistrationSchema()
        .create(UInt64.valueOf(index), pqData);
  }

  private BuilderPqRegistration builderRegistration(
      final BLSPublicKey pubkey, final Bytes32 pqData) {
    return schemaDefinitions.getBuilderPqRegistrationSchema().create(pubkey, pqData);
  }

  private void appendBuilderRegistration(final BLSPublicKey pubkey, final Bytes32 pqData) {
    state =
        BeaconStateEip8483.required(
            state.updated(
                s ->
                    MutableBeaconStateEip8483.required(s)
                        .getBuilderPqRegistrations()
                        .append(builderRegistration(pubkey, pqData))));
  }

  private void setExecutionWithdrawalAddress(final int index, final Bytes20 address) {
    setWithdrawalCredentials(
        index,
        Bytes.concatenate(Bytes.of(0x01), Bytes.wrap(new byte[11]), address.getWrappedBytes()));
  }

  private void setWithdrawalCredentials(final int index, final Bytes credentials) {
    state =
        BeaconStateEip8483.required(
            state.updated(
                s ->
                    s.getValidators()
                        .update(
                            index, v -> v.withWithdrawalCredentials(Bytes32.wrap(credentials)))));
  }

  private BLSPublicKey validatorPubkey(final int index) {
    return state.getValidators().get(index).getPublicKey();
  }

  private BLSPublicKey builderPubkey(final int index) {
    return state.getBuilders().get(index).getPublicKey();
  }

  private static Bytes20 address(final int value) {
    return new Bytes20(repeat(value, Bytes20.SIZE));
  }

  private static Bytes32 repeat32(final int value) {
    return Bytes32.wrap(repeat(value, 32));
  }

  private static Bytes repeat(final int value, final int length) {
    final byte[] bytes = new byte[length];
    Arrays.fill(bytes, (byte) value);
    return Bytes.wrap(bytes);
  }
}
