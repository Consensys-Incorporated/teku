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

package tech.pegasys.teku.spec.executionlayer;

import java.math.BigInteger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public final class BuilderBoostFactorEvaluator {

  private static final BigInteger HUNDRED_PERCENT = BigInteger.valueOf(100);

  public static final UInt64 BUILDER_BOOST_FACTOR_MAX_PROFIT = UInt64.valueOf(100);
  public static final UInt64 BUILDER_BOOST_FACTOR_PREFER_EXECUTION = UInt64.ZERO;
  public static final UInt64 BUILDER_BOOST_FACTOR_PREFER_BUILDER = UInt64.MAX_VALUE;

  private BuilderBoostFactorEvaluator() {}

  public static BigInteger computeBoostedValue(
      final BigInteger builderValue, final UInt64 builderBoostFactor) {
    return builderValue.multiply(builderBoostFactor.bigIntegerValue());
  }

  public static boolean isLocalValueWinning(
      final UInt256 localValue, final UInt256 builderValue, final UInt64 builderBoostFactor) {
    if (builderBoostFactor.equals(BUILDER_BOOST_FACTOR_PREFER_EXECUTION)) {
      return true;
    }
    if (builderBoostFactor.equals(BUILDER_BOOST_FACTOR_PREFER_BUILDER)) {
      return false;
    }
    final BigInteger boostedBuilderValue =
        computeBoostedValue(builderValue.toBigInteger(), builderBoostFactor);
    final BigInteger scaledLocalValue = localValue.toBigInteger().multiply(HUNDRED_PERCENT);
    return boostedBuilderValue.compareTo(scaledLocalValue) <= 0;
  }
}
