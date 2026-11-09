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

package tech.pegasys.teku.spec.logic.versions.electra.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDenebTest;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;

class BlockProcessorElectraTest extends BlockProcessorDenebTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetElectra();
  }

  @Test
  public void verifiesOutstandingEth1DepositsAreProcessed() {
    final BeaconState state =
        createBeaconState()
            .updated(
                mutableState -> {
                  final UInt64 eth1DepositCount = UInt64.valueOf(25);
                  mutableState.setEth1Data(
                      new Eth1Data(
                          dataStructureUtil.randomBytes32(),
                          eth1DepositCount,
                          dataStructureUtil.randomBytes32()));
                  final UInt64 eth1DepositIndex = UInt64.valueOf(13);
                  mutableState.setEth1DepositIndex(eth1DepositIndex);
                  final UInt64 depositRequestsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositRequestsStartIndex(depositRequestsStartIndex);
                });

    final BeaconBlockBody body =
        dataStructureUtil.randomBeaconBlockBody(
            // 20 - 13 = 7
            builder -> builder.deposits(dataStructureUtil.randomSszDeposits(7)));

    getBlockProcessor(state).verifyOutstandingDepositsAreProcessed(state, body);
  }

  @Test
  public void
      verifiesNoOutstandingEth1DepositsAreProcessedWhenFormerDepositMechanismHasBeenDisabled() {
    final BeaconState state =
        createBeaconState()
            .updated(
                mutableState -> {
                  final UInt64 eth1DepositCount = UInt64.valueOf(25);
                  mutableState.setEth1Data(
                      new Eth1Data(
                          dataStructureUtil.randomBytes32(),
                          eth1DepositCount,
                          dataStructureUtil.randomBytes32()));
                  final UInt64 eth1DepositIndex = UInt64.valueOf(20);
                  mutableState.setEth1DepositIndex(eth1DepositIndex);
                  final UInt64 depositRequestsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositRequestsStartIndex(depositRequestsStartIndex);
                });

    final BeaconBlockBody body =
        dataStructureUtil.randomBeaconBlockBody(
            // 20 - 20 = 0
            modifier -> modifier.deposits(dataStructureUtil.randomSszDeposits(0)));

    getBlockProcessor(state).verifyOutstandingDepositsAreProcessed(state, body);
  }

  @Override
  @Test
  public void processDepositTopsUpValidatorBalanceWhenPubkeyIsFoundInRegistry()
      throws BlockProcessingException {
    // Create a deposit
    final Bytes32 withdrawalCredentials =
        dataStructureUtil.randomCompoundingWithdrawalCredentials();
    DepositData depositInput = dataStructureUtil.randomDepositData(withdrawalCredentials);
    BLSPublicKey pubkey = depositInput.getPubkey();
    UInt64 amount = depositInput.getAmount();

    Validator knownValidator = makeValidator(pubkey, withdrawalCredentials);

    BeaconState preState = createBeaconState(amount, knownValidator);

    int originalValidatorRegistrySize = preState.getValidators().size();
    int originalValidatorBalancesSize = preState.getBalances().size();

    BeaconState postState = processDepositHelper(preState, depositInput);

    assertEquals(
        postState.getValidators().size(),
        originalValidatorRegistrySize,
        "A new validator was added to the validator registry, but should not have been.");
    assertEquals(
        postState.getBalances().size(),
        originalValidatorBalancesSize,
        "A new balance was added to the validator balances, but should not have been.");
    assertEquals(knownValidator, postState.getValidators().get(originalValidatorRegistrySize - 1));
    // as of electra, the balance increase is in the queue, and yet to be applied to the validator.
    assertEquals(amount, postState.getBalances().getElement(originalValidatorBalancesSize - 1));
    assertThat(BeaconStateElectra.required(postState).getPendingDeposits().get(0).getPublicKey())
        .isEqualTo(postState.getValidators().get(originalValidatorBalancesSize - 1).getPublicKey());
    assertThat(BeaconStateElectra.required(postState).getPendingDeposits().get(0).getAmount())
        .isEqualTo(UInt64.THIRTY_TWO_ETH);
  }

  @Test
  void shouldUseElectraAttestationUtil() {
    assertThat(spec.getGenesisSpec().getAttestationUtil())
        .isInstanceOf(AttestationUtilElectra.class);
  }

  @Test
  public void shouldCreateNewPayloadRequestWithExecutionRequestsHash() throws Exception {
    final BeaconState preState = createBeaconState();
    final BeaconBlockBodyElectra blockBody =
        BeaconBlockBodyElectra.required(dataStructureUtil.randomBeaconBlockBodyWithCommitments(3));
    final MiscHelpers miscHelpers = spec.atSlot(UInt64.ONE).miscHelpers();
    final List<VersionedHash> expectedVersionedHashes =
        blockBody.getOptionalBlobKzgCommitments().orElseThrow().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .collect(Collectors.toList());
    final List<Bytes> expectedExecutionRequests =
        spec.getExecutionRequestsDataCodec(UInt64.ONE).encode(blockBody.getExecutionRequests());

    final NewPayloadRequest newPayloadRequest =
        spec.getBlockProcessor(UInt64.ONE).computeNewPayloadRequest(preState, blockBody);

    assertThat(newPayloadRequest.getExecutionPayload())
        .isEqualTo(blockBody.getOptionalExecutionPayload().orElseThrow());
    assertThat(newPayloadRequest.getVersionedHashes()).isPresent();
    assertThat(newPayloadRequest.getVersionedHashes().get())
        .hasSize(3)
        .allSatisfy(
            versionedHash ->
                assertThat(versionedHash.getVersion())
                    .isEqualTo(SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG))
        .hasSameElementsAs(expectedVersionedHashes);
    assertThat(newPayloadRequest.getParentBeaconBlockRoot()).isPresent();
    assertThat(newPayloadRequest.getParentBeaconBlockRoot().get())
        .isEqualTo(preState.getLatestBlockHeader().getParentRoot());
    assertThat(newPayloadRequest.getExecutionRequests()).hasValue(expectedExecutionRequests);
  }

  private BlockProcessorElectra getBlockProcessor(final BeaconState state) {
    return (BlockProcessorElectra) spec.getBlockProcessor(state.getSlot());
  }
}
