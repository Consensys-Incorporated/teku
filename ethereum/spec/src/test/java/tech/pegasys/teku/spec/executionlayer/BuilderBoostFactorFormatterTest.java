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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_MAX_PROFIT;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_EXECUTION;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class BuilderBoostFactorFormatterTest {

  @Test
  void formatsSpecialFactorsByNameAndRegularFactorsAsPercentages() {
    assertThat(
            BuilderBoostFactorFormatter.formatBuilderBoostFactor(BUILDER_BOOST_FACTOR_MAX_PROFIT))
        .isEqualTo("MAX_PROFIT");
    assertThat(
            BuilderBoostFactorFormatter.formatBuilderBoostFactor(
                BUILDER_BOOST_FACTOR_PREFER_EXECUTION))
        .isEqualTo("PREFER_EXECUTION");
    assertThat(
            BuilderBoostFactorFormatter.formatBuilderBoostFactor(
                BUILDER_BOOST_FACTOR_PREFER_BUILDER))
        .isEqualTo("PREFER_BUILDER");
    assertThat(BuilderBoostFactorFormatter.formatBuilderBoostFactor(UInt64.valueOf(90)))
        .isEqualTo("90%");
  }
}
