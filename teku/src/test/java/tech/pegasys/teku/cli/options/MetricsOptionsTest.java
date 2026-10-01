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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.StandardMetricCategory.JVM;
import static org.hyperledger.besu.metrics.StandardMetricCategory.PROCESS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.EVENTBUS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P_GOSSIP;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.NETWORK;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class MetricsOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final MetricsConfig config =
        getTekuConfigurationFromFile("metricsOptions_config.yaml").metricsConfig();

    assertThat(config.getMetricsInterface()).isEqualTo("127.100.0.1");
    assertThat(config.getMetricsPort()).isEqualTo(8888);
    assertThat(config.isMetricsEnabled()).isTrue();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(JVM, PROCESS));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(TekuMetricCategory.class)
  public void metricsCategories_shouldAcceptValues(final MetricCategory category) {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-categories", category.toString());
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(category));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(StandardMetricCategory.class)
  public void metricsCategories_shouldAcceptStandardMetricCategories(
      final MetricCategory category) {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-categories", category.toString());
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(category));
  }

  @Test
  public void metricsCategories_shouldAcceptMultipleValues() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--metrics-categories", "LIBP2P,NETWORK,EVENTBUS,PROCESS");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(LIBP2P, NETWORK, EVENTBUS, PROCESS));
  }

  @Test
  public void metricsCategories_shouldNotBeCaseSensitive() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-categories", "LibP2P,network,EventBUS,PROCESS")
            .metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(LIBP2P, NETWORK, EVENTBUS, PROCESS));
  }

  @Test
  public void metricsAdditionalCategories_shouldAddToDefaultCategories() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--Xmetrics-additional-categories", "LIBP2P_GOSSIP")
            .metricsConfig();
    assertThat(config.getMetricsCategories())
        .containsAll(MetricsConfig.DEFAULT_METRICS_CATEGORIES)
        .contains(LIBP2P_GOSSIP)
        .hasSize(MetricsConfig.DEFAULT_METRICS_CATEGORIES.size() + 1);
  }

  @Test
  public void metricsAdditionalCategories_shouldAddToExplicitCategories() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments(
                "--metrics-categories",
                "LIBP2P,PROCESS",
                "--Xmetrics-additional-categories",
                "libp2p_gossip")
            .metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(LIBP2P, PROCESS, LIBP2P_GOSSIP));
  }

  @Test
  public void metricsEnabled_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments("--metrics-enabled");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.isMetricsEnabled()).isTrue();
  }

  @Test
  public void metricsHostAllowlist_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-host-allowlist");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).isEmpty();
  }

  @Test
  public void metricsHostAllowlist_shouldSupportAllowingMultipleHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-host-allowlist", "my.host,their.host");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).containsOnly("my.host", "their.host");
  }

  @Test
  public void metricsHostAllowlist_shouldSupportAllowingAllHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-host-allowlist", "*");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).containsOnly("*");
  }

  @Test
  public void metricsHostAllowlist_shouldDefaultToLocalhost() {
    assertThat(getTekuConfigurationFromArguments().metricsConfig().getMetricsHostAllowlist())
        .containsOnly("localhost", "127.0.0.1");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideArgumentsForShouldSetWarningThresholds")
  public void shouldSetWarningThresholds(
      final String option, final Function<MetricsConfig, Integer> configValueFunction) {
    final TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments(option, "100");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(configValueFunction.apply(config)).isEqualTo(100);
  }

  private static Stream<Arguments> provideArgumentsForShouldSetWarningThresholds() {
    return Stream.of(
        Arguments.of(
            "--Xmetrics-block-production-timing-tracking-warning-local-threshold",
            (Function<MetricsConfig, Integer>)
                MetricsConfig::getBlockProductionPerformanceWarningLocalThreshold),
        Arguments.of(
            "--Xmetrics-block-production-timing-tracking-warning-builder-threshold",
            (Function<MetricsConfig, Integer>)
                MetricsConfig::getBlockProductionPerformanceWarningBuilderThreshold),
        Arguments.of(
            "--Xmetrics-block-publishing-timing-tracking-warning-local-threshold",
            (Function<MetricsConfig, Integer>)
                MetricsConfig::getBlockPublishingPerformanceWarningLocalThreshold),
        Arguments.of(
            "--Xmetrics-block-publishing-timing-tracking-warning-builder-threshold",
            (Function<MetricsConfig, Integer>)
                MetricsConfig::getBlockPublishingPerformanceWarningBuilderThreshold));
  }
}
