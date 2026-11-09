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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfigSchema.MAX_BUILDER_ENTRIES;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.UInt64Converter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.BuilderUrlParser;

/**
 * Configuration options for ePBS
 *
 * @see <a href="https://github.com/ethereum/builder-specs/tree/main/specs/gloas">builder-specs</a>
 */
public class BuilderOptions {

  @CommandLine.Option(
      names = {"--builder-min-bid"},
      paramLabel = "<uint64>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          "Minimum total payment, in Gwei, accepted from bids of the configured builders. A bid whose `value` plus counted `execution_payment` is below this MUST be rejected.",
      arity = "1",
      converter = UInt64Converter.class)
  private UInt64 builderMinBid = ValidatorConfig.DEFAULT_BUILDER_MIN_BID;

  @CommandLine.Option(
      names = {"--p2p-builder-min-bid"},
      paramLabel = "<uint64>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          "Minimum payment, in Gwei, accepted from bids received over p2p. A bid whose `value` is below this MUST be rejected.",
      arity = "1",
      converter = UInt64Converter.class)
  private UInt64 p2pBuilderMinBid = ValidatorConfig.DEFAULT_P2P_BUILDER_MIN_BID;

  @CommandLine.Option(
      names = {"--builder-boost-factor"},
      paramLabel = "<uint64>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          """
            Percentage multiplier applied to bids of the configured builders: `0` prefers the local
            payload, `100` is profit maximization, and `2**64 - 1` prefers the bid, each unless
            an error or health check makes the preferred payload unviable.""",
      arity = "1",
      converter = UInt64Converter.class)
  private UInt64 builderBoostFactor = ValidatorConfig.DEFAULT_BUILDER_BOOST_FACTOR;

  @CommandLine.Option(
      names = {"--p2p-builder-boost-factor"},
      paramLabel = "<uint64>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          """
            Percentage multiplier applied to bids received over p2p: `0` prefers the local
            payload, `100` is profit maximization, and `2**64 - 1` prefers the bid, each unless
            an error or health check makes the preferred payload unviable.""",
      arity = "1",
      converter = UInt64Converter.class)
  private UInt64 p2pBuilderBoostFactor = ValidatorConfig.DEFAULT_P2P_BUILDER_BOOST_FACTOR;

  @CommandLine.Option(
      names = {"--builder-max-execution-payment"},
      paramLabel = "<uint64>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          """
            The maximum execution layer payment (in Gwei) that counts when a builder's bid is valued.
            `0` is the default which counts no execution payment, leaving the bid valued at its trustless value alone.
            This parameter may be adjusted based on the level of trust in the builder's reliability and reputation.""",
      arity = "1",
      converter = UInt64Converter.class)
  private UInt64 builderMaxExecutionPayment = ValidatorConfig.DEFAULT_BUILDER_MAX_EXECUTION_PAYMENT;

  @CommandLine.Option(
      names = {"--builder-urls"},
      paramLabel = "<url>",
      description = "Comma separated list of urls of builders to use when proposing",
      split = ",",
      arity = "0..*")
  private final List<String> builderUrls = new ArrayList<>();

  public void configure(final TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .builderMinBid(builderMinBid)
                .builderBoostFactor(builderBoostFactor)
                .p2pBuilderMinBid(p2pBuilderMinBid)
                .p2pBuilderBoostFactor(p2pBuilderBoostFactor)
                .builderMaxExecutionPayment(builderMaxExecutionPayment)
                .builderUrls(parseBuilderUrls()));
  }

  private List<URL> parseBuilderUrls() {
    if (builderUrls.size() > MAX_BUILDER_ENTRIES) {
      throw new InvalidConfigurationException(
          String.format(
              "Invalid configuration. Builders must be at most %s but are %s",
              MAX_BUILDER_ENTRIES, builderUrls.size()));
    }
    return builderUrls.stream().map(BuilderUrlParser::parseBuilderUrl).toList();
  }
}
