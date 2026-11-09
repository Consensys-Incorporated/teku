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

package tech.pegasys.teku.spec.datastructures.interop;

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintToBytes32;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.infrastructure.crypto.Hash;

public class MockStartValidatorKeyPairFactory {
  private static final BigInteger CURVE_ORDER =
      new BigInteger(
          "52435875175126190479447740508185965837690552500527637822603658699938581184513");

  public List<BLSKeyPair> generateKeyPairs(final int startIndex, final int endIndex) {
    return IntStream.range(startIndex, endIndex).mapToObj(this::createKeyPairForValidator).toList();
  }

  private BLSKeyPair createKeyPairForValidator(final int validatorIndex) {
    final Bytes hash = Hash.sha256(uintToBytes32(validatorIndex));
    final BigInteger privKey = hash.reverse().toUnsignedBigInteger().mod(CURVE_ORDER);
    final Bytes32 privKeyBytes = Bytes32.leftPad(Bytes.of(privKey.toByteArray()));

    return new BLSKeyPair(BLSSecretKey.fromBytes(privKeyBytes));
  }
}
