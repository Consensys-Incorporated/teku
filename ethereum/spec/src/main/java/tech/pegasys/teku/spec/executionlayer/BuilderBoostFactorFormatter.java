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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public final class BuilderBoostFactorFormatter {

  private BuilderBoostFactorFormatter() {}

  public static String formatBuilderBoostFactor(final UInt64 builderBoostFactor) {
    if (builderBoostFactor.equals(BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_MAX_PROFIT)) {
      return "MAX_PROFIT";
    }
    if (builderBoostFactor.equals(
        BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_EXECUTION)) {
      return "PREFER_EXECUTION";
    }
    if (builderBoostFactor.equals(
        BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER)) {
      return "PREFER_BUILDER";
    }
    return builderBoostFactor + "%";
  }
}
