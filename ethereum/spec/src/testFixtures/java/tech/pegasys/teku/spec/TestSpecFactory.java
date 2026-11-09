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

package tech.pegasys.teku.spec;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.NoOpKZG;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestSpecFactory {

  public static Spec createDefault() {
    return createDefault(__ -> {});
  }

  public static Spec createDefault(final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName(), modifier);
    return create(config, SpecMilestone.PHASE0);
  }

  public static Spec createMinimal(final SpecMilestone specMilestone) {
    return switch (specMilestone) {
      case PHASE0 -> createMinimalPhase0();
      case ALTAIR -> createMinimalAltair();
      case BELLATRIX -> createMinimalBellatrix();
      case CAPELLA -> createMinimalCapella();
      case DENEB -> createMinimalDeneb();
      case ELECTRA -> createMinimalElectra();
      case FULU -> createMinimalFulu();
      case GLOAS -> createMinimalGloas();
      case HEZE -> createMinimalHeze();
    };
  }

  public static Spec createMainnet(final SpecMilestone specMilestone) {
    return switch (specMilestone) {
      case PHASE0 -> createMainnetPhase0();
      case ALTAIR -> createMainnetAltair();
      case BELLATRIX -> createMainnetBellatrix();
      case CAPELLA -> createMainnetCapella();
      case DENEB -> createMainnetDeneb();
      case ELECTRA -> createMainnetElectra();
      case FULU -> createMainnetFulu();
      case GLOAS -> createMainnetGloas();
      case HEZE -> createMainnetHeze();
    };
  }

  public static Spec createMinimalWithAltairAndBellatrixForkEpoch(
      final UInt64 altairEpoch, final UInt64 bellatrixForkEpoch) {
    Preconditions.checkArgument(
        altairEpoch.isLessThan(bellatrixForkEpoch),
        "Altair epoch %s must be less than bellatrix epoch %s",
        altairEpoch,
        bellatrixForkEpoch);
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, altairEpoch, bellatrixForkEpoch);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalAltair() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getAltairSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMinimalCapella() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getCapellaSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMinimalDeneb() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getDenebSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMinimalDeneb(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getDenebSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMinimalElectra() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getElectraSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.ELECTRA);
  }

  public static Spec createMinimalElectra(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getElectraSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.ELECTRA);
  }

  public static Spec createMinimalFulu() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getFuluSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.FULU);
  }

  public static Spec createMinimalFulu(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getFuluSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.FULU);
  }

  public static Spec createMinimalGloas() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getGloasSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.GLOAS);
  }

  public static Spec createMinimalGloas(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getGloasSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.GLOAS);
  }

  public static Spec createMinimalHeze() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getHezeSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.HEZE);
  }

  public static Spec createMinimalHeze(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getHezeSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.HEZE);
  }

  /**
   * Create a spec that forks to altair at the provided slot
   *
   * @param altairForkEpoch The altair fork epoch
   * @return A spec with phase0 and altair enabled, forking to altair at the given epoch
   */
  public static Spec createMinimalWithAltairForkEpoch(final UInt64 altairForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getAltairSpecConfig(Eth2Network.MINIMAL, altairForkEpoch);
    return create(config, SpecMilestone.ALTAIR);
  }

  /**
   * Create a spec that forks to bellatrix at the provided slot (altair genesis)
   *
   * @param bellatrixForkEpoch The bellatrix fork epoch
   * @return A spec with altair and bellatrix enabled, forking to bellatrix at the given epoch
   */
  public static Spec createMinimalWithBellatrixForkEpoch(final UInt64 bellatrixForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, bellatrixForkEpoch);
    return create(config, SpecMilestone.BELLATRIX);
  }

  /**
   * Create a spec that forks to Capella at the provided slot (bellatrix genesis)
   *
   * @param capellaForkEpoch The Capella fork epoch
   * @return A spec with Altair, Bellatrix and Capella enabled, forking to Capella at the given
   *     epoch
   */
  public static Spec createMinimalWithCapellaForkEpoch(final UInt64 capellaForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getCapellaSpecConfig(Eth2Network.MINIMAL, capellaForkEpoch);
    return create(config, SpecMilestone.CAPELLA);
  }

  /**
   * Create a spec that forks to Deneb at the provided epoch
   *
   * @param denebForkEpoch The Deneb fork epoch
   * @return A spec with Deneb enabled, forking to Deneb at the given epoch
   */
  public static Spec createMinimalWithDenebForkEpoch(final UInt64 denebForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getDenebSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, denebForkEpoch, __ -> {});
    return create(config, SpecMilestone.DENEB);
  }

  public static Spec createMinimalWithDenebForkEpoch(
      final UInt64 denebForkEpoch, final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getDenebSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, denebForkEpoch, configAdapter);
    return create(config, SpecMilestone.DENEB);
  }

  /**
   * Create a spec that forks to Electra at the provided epoch
   *
   * @param electraForkEpoch The Electra fork epoch
   * @return A spec with Electra enabled, forking to Electra at the given epoch
   */
  public static Spec createMinimalWithElectraForkEpoch(final UInt64 electraForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getElectraSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, UInt64.ZERO, electraForkEpoch);
    return create(config, SpecMilestone.ELECTRA);
  }

  /**
   * Create a spec that forks to Fulu at the provided epoch
   *
   * @param fuluForkEpoch The Fulu fork epoch
   * @return A spec with Fulu enabled, forking to Fulu at the given epoch
   */
  public static Spec createMinimalWithFuluForkEpoch(final UInt64 fuluForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getFuluSpecConfig(
            Eth2Network.MINIMAL, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, fuluForkEpoch);
    return create(config, SpecMilestone.FULU);
  }

  /**
   * Create a spec that forks to Gloas at the provided epoch
   *
   * @param gloasForkEpoch The Gloas fork epoch
   * @return A spec with Gloas enabled, forking to Gloas at the given epoch
   */
  public static Spec createMinimalWithGloasForkEpoch(final UInt64 gloasForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getGloasSpecConfig(
            Eth2Network.MINIMAL,
            UInt64.ZERO,
            UInt64.ZERO,
            UInt64.ZERO,
            UInt64.ZERO,
            gloasForkEpoch);
    return create(config, SpecMilestone.GLOAS);
  }

  /**
   * Create a spec that forks to Heze at the provided epoch
   *
   * @param hezeForkEpoch The Heze fork epoch
   * @return A spec with Heze enabled, forking to Heze at the given epoch
   */
  public static Spec createMinimalWithHezeForkEpoch(final UInt64 hezeForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getHezeSpecConfig(
            Eth2Network.MINIMAL,
            UInt64.ZERO,
            UInt64.ZERO,
            UInt64.ZERO,
            UInt64.ZERO,
            UInt64.ZERO,
            hezeForkEpoch);
    return create(config, SpecMilestone.HEZE);
  }

  public static Spec createMinimalPhase0(final Consumer<SpecConfigBuilder> configAdapter) {
    return create(SpecMilestone.PHASE0, Eth2Network.MINIMAL, configAdapter);
  }

  public static Spec createMinimalPhase0() {
    final SpecConfigAndParent<? extends SpecConfig> configAndParent =
        SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());
    return create(configAndParent, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetPhase0() {
    final SpecConfigAndParent<? extends SpecConfig> configAndParent =
        SpecConfigLoader.loadConfig(Eth2Network.MAINNET.configName());
    return create(configAndParent, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetPhase0(final Consumer<SpecConfigBuilder> configAdapter) {
    return create(SpecMilestone.PHASE0, Eth2Network.MAINNET, configAdapter);
  }

  public static Spec createMainnetBellatrix() {
    final SpecConfigAndParent<? extends SpecConfig> configAndParent =
        getBellatrixSpecConfig(Eth2Network.MAINNET);
    return create(configAndParent, SpecMilestone.BELLATRIX);
  }

  public static Spec createMainnetAltair() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getAltairSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMainnetAltair(final Consumer<SpecConfigBuilder> configAdapter) {
    return create(SpecMilestone.ALTAIR, Eth2Network.MAINNET, configAdapter);
  }

  public static Spec createMainnetCapella() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getCapellaSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMainnetDeneb() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getDenebSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMainnetElectra() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getElectraSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.ELECTRA);
  }

  public static Spec createMainnetFulu() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getFuluSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.FULU);
  }

  public static Spec createMainnetGloas() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getGloasSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.GLOAS);
  }

  public static Spec createMainnetHeze() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getHezeSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.HEZE);
  }

  public static Spec createPhase0(final SpecConfigAndParent<? extends SpecConfig> config) {
    return create(config, SpecMilestone.PHASE0);
  }

  public static Spec createAltair(final SpecConfigAndParent<? extends SpecConfig> config) {
    return create(config, SpecMilestone.ALTAIR);
  }

  public static Spec createBellatrix(final SpecConfigAndParent<? extends SpecConfig> config) {
    return create(config, SpecMilestone.BELLATRIX);
  }

  public static Spec create(final SpecMilestone specMilestone, final Eth2Network network) {
    return create(specMilestone, network, builder -> {});
  }

  public static Spec create(
      final SpecMilestone specMilestone,
      final Eth2Network network,
      final Consumer<SpecConfigBuilder> configModifier) {
    return create(specMilestone, network, configModifier, Collections.emptyMap());
  }

  public static Spec create(
      final SpecMilestone specMilestone,
      final Eth2Network network,
      final Consumer<SpecConfigBuilder> configModifier,
      final Map<String, Object> configOverrides) {

    Consumer<SpecConfigBuilder> defaultModifier = __ -> {};
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.altairForkEpoch(UInt64.ZERO));
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.bellatrixForkEpoch(UInt64.ZERO));
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.capellaForkEpoch(UInt64.ZERO));
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.denebForkEpoch(UInt64.ZERO));
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.electraForkEpoch(UInt64.ZERO));
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.fuluForkEpoch(UInt64.ZERO));
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.gloasForkEpoch(UInt64.ZERO));
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.HEZE)) {
      defaultModifier = defaultModifier.andThen(builder -> builder.hezeForkEpoch(UInt64.ZERO));
    }

    return create(
        SpecConfigLoader.loadConfig(
            network.configName(), defaultModifier.andThen(configModifier), configOverrides),
        specMilestone);
  }

  public static Spec create(
      final SpecConfigAndParent<? extends SpecConfig> config,
      final SpecMilestone highestSupportedMilestone) {
    final Spec spec = Spec.create(config, highestSupportedMilestone);

    spec.initialize(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        NoOpKZG.INSTANCE);
    return spec;
  }

  private static SpecConfigAndParent<? extends SpecConfig> getAltairSpecConfig(
      final Eth2Network network) {
    return getAltairSpecConfig(network, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getAltairSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch) {
    return requireAltair(
        SpecConfigLoader.loadConfig(
            network.configName(), builder -> builder.altairForkEpoch(altairForkEpoch)));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getBellatrixSpecConfig(
      final Eth2Network network) {
    return getBellatrixSpecConfig(network, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getBellatrixSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch, final UInt64 bellatrixForkEpoch) {
    return getBellatrixSpecConfig(
        network,
        builder -> builder.altairForkEpoch(altairForkEpoch).bellatrixForkEpoch(bellatrixForkEpoch));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getBellatrixSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireBellatrix(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder.altairForkEpoch(ZERO).bellatrixForkEpoch(ZERO);
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getCapellaSpecConfig(
      final Eth2Network network) {
    return getCapellaSpecConfig(network, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getCapellaSpecConfig(
      final Eth2Network network, final UInt64 capellaForkEpoch) {
    return getCapellaSpecConfig(
        network,
        builder ->
            builder
                .altairForkEpoch(ZERO)
                .bellatrixForkEpoch(ZERO)
                .capellaForkEpoch(capellaForkEpoch));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getCapellaSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireCapella(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder.altairForkEpoch(ZERO).bellatrixForkEpoch(ZERO).capellaForkEpoch(ZERO);
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getDenebSpecConfig(
      final Eth2Network network) {
    return getDenebSpecConfig(network, ZERO, ZERO, __ -> {});
  }

  private static SpecConfigAndParent<? extends SpecConfig> getDenebSpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final Consumer<SpecConfigBuilder> configAdapter) {
    return getDenebSpecConfig(
        network,
        builder -> {
          builder
              .altairForkEpoch(ZERO)
              .bellatrixForkEpoch(ZERO)
              .capellaForkEpoch(capellaForkEpoch)
              .denebForkEpoch(denebForkEpoch);
          configAdapter.accept(builder);
        });
  }

  private static SpecConfigAndParent<? extends SpecConfig> getDenebSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireDeneb(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairForkEpoch(ZERO)
                  .bellatrixForkEpoch(ZERO)
                  .capellaForkEpoch(ZERO)
                  .denebForkEpoch(ZERO);
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getElectraSpecConfig(
      final Eth2Network network) {
    return getElectraSpecConfig(network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getElectraSpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch) {
    return getElectraSpecConfig(
        network,
        builder ->
            builder
                .altairForkEpoch(ZERO)
                .bellatrixForkEpoch(ZERO)
                .capellaForkEpoch(capellaForkEpoch)
                .denebForkEpoch(denebForkEpoch)
                .electraForkEpoch(electraForkEpoch));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getElectraSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireElectra(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairForkEpoch(ZERO)
                  .bellatrixForkEpoch(ZERO)
                  .capellaForkEpoch(ZERO)
                  .denebForkEpoch(ZERO)
                  .electraForkEpoch(ZERO);
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getFuluSpecConfig(
      final Eth2Network network) {
    return getFuluSpecConfig(network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getFuluSpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch,
      final UInt64 fuluForkEpoch) {
    return getFuluSpecConfig(
        network,
        builder ->
            builder
                .altairForkEpoch(ZERO)
                .bellatrixForkEpoch(UInt64.ZERO)
                .capellaForkEpoch(capellaForkEpoch)
                .denebForkEpoch(denebForkEpoch)
                .electraForkEpoch(electraForkEpoch)
                .fuluForkEpoch(fuluForkEpoch));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getFuluSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireFulu(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairForkEpoch(ZERO)
                  .bellatrixForkEpoch(UInt64.ZERO)
                  .capellaForkEpoch(UInt64.ZERO)
                  .denebForkEpoch(UInt64.ZERO)
                  .electraForkEpoch(UInt64.ZERO)
                  .fuluForkEpoch(UInt64.ZERO);
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getGloasSpecConfig(
      final Eth2Network network) {
    return getGloasSpecConfig(
        network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getGloasSpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch,
      final UInt64 fuluForkEpoch,
      final UInt64 gloasForkEpoch) {
    return getGloasSpecConfig(
        network,
        builder ->
            builder
                .altairForkEpoch(ZERO)
                .bellatrixForkEpoch(UInt64.ZERO)
                .capellaForkEpoch(capellaForkEpoch)
                .denebForkEpoch(denebForkEpoch)
                .electraForkEpoch(electraForkEpoch)
                .fuluForkEpoch(fuluForkEpoch)
                .gloasForkEpoch(gloasForkEpoch));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getGloasSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireGloas(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairForkEpoch(ZERO)
                  .bellatrixForkEpoch(UInt64.ZERO)
                  .capellaForkEpoch(UInt64.ZERO)
                  .denebForkEpoch(UInt64.ZERO)
                  .electraForkEpoch(UInt64.ZERO)
                  .fuluForkEpoch(UInt64.ZERO)
                  .gloasForkEpoch(ZERO);
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getHezeSpecConfig(
      final Eth2Network network) {
    return getHezeSpecConfig(
        network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getHezeSpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch,
      final UInt64 fuluForkEpoch,
      final UInt64 gloasForkEpoch,
      final UInt64 hezeForkEpoch) {
    return getHezeSpecConfig(
        network,
        builder ->
            builder
                .altairForkEpoch(ZERO)
                .bellatrixForkEpoch(UInt64.ZERO)
                .capellaForkEpoch(capellaForkEpoch)
                .denebForkEpoch(denebForkEpoch)
                .electraForkEpoch(electraForkEpoch)
                .fuluForkEpoch(fuluForkEpoch)
                .gloasForkEpoch(gloasForkEpoch)
                .hezeForkEpoch(hezeForkEpoch));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getHezeSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireHeze(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairForkEpoch(ZERO)
                  .bellatrixForkEpoch(UInt64.ZERO)
                  .capellaForkEpoch(UInt64.ZERO)
                  .denebForkEpoch(UInt64.ZERO)
                  .electraForkEpoch(UInt64.ZERO)
                  .fuluForkEpoch(UInt64.ZERO)
                  .gloasForkEpoch(UInt64.ZERO)
                  .hezeForkEpoch(ZERO);
              configAdapter.accept(builder);
            }));
  }

  public static Spec createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch,
      final UInt64 fuluForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getFuluSpecConfig(
            Eth2Network.MINIMAL, capellaForkEpoch, denebForkEpoch, electraForkEpoch, fuluForkEpoch);
    return create(config, SpecMilestone.FULU);
  }

  public static Spec createMinimalWithCapellaDenebElectraFuluGloasAndHezeForkEpoch(
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch,
      final UInt64 fuluForkEpoch,
      final UInt64 gloasForkEpoch,
      final UInt64 hezeForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getHezeSpecConfig(
            Eth2Network.MINIMAL,
            capellaForkEpoch,
            denebForkEpoch,
            electraForkEpoch,
            fuluForkEpoch,
            gloasForkEpoch,
            hezeForkEpoch);
    return create(config, SpecMilestone.HEZE);
  }

  // Our current config files contain Fulu params.
  // So all specConfigs created from them will be Fulu.
  // Here we just want to make sure that a given config supports the given milestone
  // (which useless in theory because they are all Fulu)

  private static SpecConfigAndParent<? extends SpecConfig> requireAltair(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionAltair().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireBellatrix(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionBellatrix().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireCapella(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionCapella().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireDeneb(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionDeneb().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireElectra(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionElectra().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireFulu(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionFulu().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireGloas(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionGloas().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireHeze(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionHeze().isPresent());
    return specConfigAndParent;
  }
}
