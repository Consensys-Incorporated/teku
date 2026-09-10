/*
 * Copyright Consensys Software Inc., 2026
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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.UInt64Converter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorConfig;

/**
 * Configuration options for ePBS
 *
 * @see <a href="https://github.com/ethereum/builder-specs/tree/main/specs/gloas">builder-specs</a>
 */
public class BuilderOptions {

  @CommandLine.Option(
      names = {"--Xbuilder-min-bid"},
      paramLabel = "<uint64>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          "Minimum total payment, in Gwei, accepted from bids (p2p or builder). A bid whose `value` plus `execution_payment` is below this MUST be rejected.",
      arity = "1",
      hidden = true,
      converter = UInt64Converter.class)
  private UInt64 builderMinBid = ValidatorConfig.DEFAULT_BUILDER_MIN_BID;

  @CommandLine.Option(
      names = {"--Xbuilder-boost-factor"},
      paramLabel = "<uint64>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          """
            Percentage multiplier applied to a bid (p2p or builder): `0` prefers the local
            payload, `100` is profit maximization, and `2**64 - 1` prefers the bid, each unless
            an error or health check makes the preferred payload unviable.""",
      arity = "1",
      hidden = true,
      converter = UInt64Converter.class)
  private UInt64 builderBoostFactor = ValidatorConfig.DEFAULT_BUILDER_BOOST_FACTOR;

  @CommandLine.Option(
      names = {"--Xbuilder-urls"},
      paramLabel = "<url>",
      description = "Comma separated list of urls of builders to use when proposing",
      split = ",",
      arity = "0..*",
      hidden = true)
  private final List<String> builderUrls = new ArrayList<>();

  public void configure(final TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .builderMinBid(builderMinBid)
                .builderBoostFactor(builderBoostFactor)
                .builderUrls(parseBuilderUrls()));
  }

  private List<URL> parseBuilderUrls() {
    return builderUrls.stream().map(this::parseBuilderUrl).toList();
  }

  private URL parseBuilderUrl(final String builderUrl) {
    try {
      return URI.create(builderUrl).toURL();
    } catch (IllegalArgumentException | MalformedURLException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Builder URL (" + builderUrl + ") has invalid syntax", e);
    }
  }
}
