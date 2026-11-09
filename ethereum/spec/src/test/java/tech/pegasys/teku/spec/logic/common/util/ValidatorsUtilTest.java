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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ValidatorsUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorsUtil validatorsUtil = spec.getGenesisSpec().getValidatorsUtil();

  @Test
  void getValidatorIndex_shouldReturnValidatorIndex() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    assertThat(state.getValidators()).hasSizeGreaterThan(5);
    for (int i = 0; i < 5; i++) {
      final Validator validator = state.getValidators().get(i);
      assertThat(
              validatorsUtil.getValidatorIndex(
                  state, BLSPublicKey.fromBytesCompressed(validator.getPubkeyBytes())))
          .contains(i);
    }
  }

  @Test
  public void getValidatorIndex_shouldReturnEmptyWhenValidatorNotFound() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Optional<Integer> index =
        validatorsUtil.getValidatorIndex(state, dataStructureUtil.randomPublicKey());
    assertThat(index).isEmpty();
  }

  @Test
  public void getValidatorIndex_shouldReturnEmptyWhenValidatorInCacheButNotState() {
    // The public key to index cache is shared between all states (because validator indices are
    // effectively set by the eth1 chain so are consistent across forks).
    // However we need to ensure we don't return a value added to the cache by a later state with
    // more validators, if the validator isn't actually in the target state.
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Validator validator = dataStructureUtil.randomValidator();
    final BeaconState nextState = state.updated(s -> s.getValidators().append(validator));

    assertThat(
            validatorsUtil.getValidatorIndex(
                nextState, BLSPublicKey.fromBytesCompressed(validator.getPubkeyBytes())))
        .contains(nextState.getValidators().size() - 1);
    assertThat(
            validatorsUtil.getValidatorIndex(
                state, BLSPublicKey.fromBytesCompressed(validator.getPubkeyBytes())))
        .isEmpty();
  }

  @Test
  public void getValidatorIndex_shouldNotCacheValidatorMissing() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    final Validator validator = dataStructureUtil.randomValidator();
    // Lookup the validator before it's in the list.
    assertThat(
            validatorsUtil.getValidatorIndex(
                state, BLSPublicKey.fromBytesCompressed(validator.getPubkeyBytes())))
        .isEmpty();

    // Then add it to the list and we should be able to find the index.
    final BeaconState nextState = state.updated(s -> s.getValidators().append(validator));
    assertThat(
            validatorsUtil.getValidatorIndex(
                nextState, BLSPublicKey.fromBytesCompressed(validator.getPubkeyBytes())))
        .contains(nextState.getValidators().size() - 1);
  }

  @Test
  void testIsAggregatorReturnsFalseOnARealCase() {
    Bytes signingRoot =
        spec.getGenesisSpec()
            .miscHelpers()
            .computeSigningRoot(
                UInt64.valueOf(57950),
                Bytes32.fromHexString(
                    "0x05000000b5303f2ad2010d699a76c8e62350947421a3e4a979779642cfdb0f66"));
    BLSSignature selectionProof =
        BLSSignature.fromSSZBytes(
            Bytes.fromHexString(
                "0xaa176502f0a5e954e4c6b452d0e11a03513c19b6d189f125f07b6c5c120df011c31da4c4a9c4a52a5a48fcba5b14d7b316b986a146187966d2341388bbf1f86c42e90553ba009ba10edc6b5544a6e945ce6d2419197f66ab2b9df2b0a0c89987"));
    BLSPublicKey pKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0xb0861f72583516b17a3fdc33419d5c04c0a4444cc2478136b4935f3148797699e3ef4a4b2227b14876b3d49ff03b796d"));
    int committeeLen = 146;

    assertThat(BLS.verify(pKey, signingRoot, selectionProof)).isTrue();

    int aggregatorModulo = validatorsUtil.getAggregatorModulo(committeeLen);
    assertThat(validatorsUtil.isAggregator(selectionProof, aggregatorModulo)).isFalse();
  }

  @Test
  void getAggregatorModulo_samples() {
    final ValidatorsUtil validatorsUtil = spec.getGenesisSpec().getValidatorsUtil();

    assertThat(validatorsUtil.getAggregatorModulo(-1)).isEqualTo(1);
    assertThat(validatorsUtil.getAggregatorModulo(0)).isEqualTo(1);
    assertThat(validatorsUtil.getAggregatorModulo(1)).isEqualTo(1);
    assertThat(validatorsUtil.getAggregatorModulo(15)).isEqualTo(1);
    assertThat(validatorsUtil.getAggregatorModulo(16)).isEqualTo(1);
    assertThat(validatorsUtil.getAggregatorModulo(31)).isEqualTo(1);
    assertThat(validatorsUtil.getAggregatorModulo(32)).isEqualTo(2);
    assertThat(validatorsUtil.getAggregatorModulo(47)).isEqualTo(2);
    assertThat(validatorsUtil.getAggregatorModulo(48)).isEqualTo(3);
    assertThat(validatorsUtil.getAggregatorModulo(160)).isEqualTo(10);
    assertThat(validatorsUtil.getAggregatorModulo(16000)).isEqualTo(1000);
    assertThat(validatorsUtil.getAggregatorModulo(Integer.MAX_VALUE)).isEqualTo(134217727);
  }
}
