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

package tech.pegasys.teku.services.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.BUILDER_ALWAYS_KEYWORD;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_BID_COMPARE_FACTOR;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class ExecutionLayerConfigurationTest {
  private final ExecutionLayerConfiguration.Builder configBuilder =
      ExecutionLayerConfiguration.builder();
  private final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();

  @Test
  public void shouldThrowExceptionIfNoEeEndpointSpecified() {
    final ExecutionLayerConfiguration config = configBuilder.specProvider(bellatrixSpec).build();

    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::getEngineEndpoint)
        .withMessageContaining(
            "Invalid configuration. --ee-endpoint parameter is mandatory when Bellatrix milestone is enabled");
  }

  @Test
  public void shouldThrowExceptionIfBuilderBidCompareFactorNegative() {
    final ExecutionLayerConfiguration.Builder builder =
        configBuilder.specProvider(bellatrixSpec).builderBidCompareFactor("-100");

    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Expecting a number >= 0, percentage or "
                + BUILDER_ALWAYS_KEYWORD
                + " keyword for Builder bid compare factor");
  }

  @Test
  public void shouldThrowExceptionIfBuilderBidCompareFactorWrongWord() {
    final ExecutionLayerConfiguration.Builder builder =
        configBuilder.specProvider(bellatrixSpec).builderBidCompareFactor("OUCH");

    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Expecting a number >= 0, percentage or "
                + BUILDER_ALWAYS_KEYWORD
                + " keyword for Builder bid compare factor");
  }

  @Test
  public void shouldParseBuilderBidCompareFactor() {
    final ExecutionLayerConfiguration.Builder builder1 =
        configBuilder.specProvider(bellatrixSpec).builderBidCompareFactor("50");
    assertThat(builder1.build().getBuilderBidCompareFactor())
        .isEqualByComparingTo(UInt64.valueOf(50));
    final ExecutionLayerConfiguration.Builder builder2 =
        configBuilder.specProvider(bellatrixSpec).builderBidCompareFactor("50%");
    assertThat(builder2.build().getBuilderBidCompareFactor())
        .isEqualByComparingTo(UInt64.valueOf(50));
    final ExecutionLayerConfiguration.Builder builder3 =
        configBuilder.specProvider(bellatrixSpec).builderBidCompareFactor("Builder_ALWAYS");
    assertThat(builder3.build().getBuilderBidCompareFactor())
        .isEqualByComparingTo(BUILDER_BOOST_FACTOR_PREFER_BUILDER);
  }

  @Test
  public void shouldHaveCorrectDefaultBuilderBidCompareFactor() {
    final ExecutionLayerConfiguration.Builder builder1 = configBuilder.specProvider(bellatrixSpec);
    assertThat(builder1.build().getBuilderBidCompareFactor())
        .isEqualByComparingTo(DEFAULT_BUILDER_BID_COMPARE_FACTOR);
  }

  @Test
  public void noExceptionThrownIfEeEndpointSpecified() {
    final ExecutionLayerConfiguration config =
        configBuilder.specProvider(bellatrixSpec).engineEndpoint("someEndpoint").build();

    assertThatCode(config::getEngineEndpoint).doesNotThrowAnyException();
  }
}
