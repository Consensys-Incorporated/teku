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

package tech.pegasys.teku.test.acceptance.dsl.tools.deposits;

import static tech.pegasys.teku.spec.datastructures.util.DepositGenerator.createWithdrawalCredentials;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;

public class ValidatorKeyGenerator {
  private final SecureRandom srng = SecureRandomProvider.createSecureRandom();

  public ValidatorKeystores generateValidatorKeys(
      final int numberOfValidators, final Eth1Address withdrawalAddress) {
    return new ValidatorKeystores(
        generateKeysStream(numberOfValidators, withdrawalAddress).collect(Collectors.toList()));
  }

  public ValidatorKeystores generateValidatorKeys(final int numberOfValidators) {
    return generateValidatorKeys(numberOfValidators, false);
  }

  public ValidatorKeystores generateValidatorKeys(
      final int numberOfValidators, final boolean isLocked) {
    return new ValidatorKeystores(
        generateKeysStream(numberOfValidators, isLocked).collect(Collectors.toList()));
  }

  private Stream<ValidatorKeys> generateKeysStream(
      final int validatorCount, final boolean lockedKeys) {
    return IntStream.range(0, validatorCount)
        .mapToObj(
            ignore ->
                new ValidatorKeys(BLSKeyPair.random(srng), BLSKeyPair.random(srng), lockedKeys));
  }

  private Stream<ValidatorKeys> generateKeysStream(
      final int validatorCount, final Bytes20 eth1WithdrawalAddress) {
    final Bytes32 withdrawalCredentials = createWithdrawalCredentials(eth1WithdrawalAddress);
    return IntStream.range(0, validatorCount)
        .mapToObj(__ -> new ValidatorKeys(BLSKeyPair.random(srng), withdrawalCredentials, false));
  }
}
