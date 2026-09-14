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

package tech.pegasys.teku.spec.config;

import static tech.pegasys.teku.spec.networks.Eth2Network.EPHEMERY;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.networks.Eth2Presets;

public class SpecConfigLoader {
  private static final Logger LOG = LogManager.getLogger();
  public static final String EPHEMERY_CONFIG_URL =
      "https://ephemery.dev/latest/metadata/config.yaml";
  private static final List<String> AVAILABLE_PRESETS =
      List.of(
          "phase0", "altair", "bellatrix", "capella", "deneb", "electra", "fulu", "gloas", "heze");
  private static final List<String> BUILTIN_NETWORKS =
      List.of(
          "chiado",
          "gnosis",
          "holesky",
          "hoodi",
          "lukso",
          "less-swift",
          "mainnet",
          "minimal",
          "plataberget",
          "sepolia",
          "swift");
  private static final String CONFIG_PATH = "configs/";
  private static final String PRESET_PATH = "presets/";

  public static SpecConfigAndParent<? extends SpecConfig> loadConfigStrict(
      final String configName) {
    return loadConfig(configName, false, __ -> {});
  }

  public static SpecConfigAndParent<? extends SpecConfig> loadConfig(final String configName) {
    return loadConfig(configName, __ -> {});
  }

  public static SpecConfigAndParent<? extends SpecConfig> loadConfig(
      final String configName, final Consumer<SpecConfigBuilder> modifier) {
    return loadConfig(configName, true, modifier);
  }

  public static SpecConfigAndParent<? extends SpecConfig> loadConfig(
      final String configName,
      final Consumer<SpecConfigBuilder> modifier,
      final Map<String, Object> configOverrides) {
    return loadConfig(configName, true, true, modifier, configOverrides);
  }

  public static SpecConfigAndParent<? extends SpecConfig> loadConfig(
      final String configName,
      final boolean strictConfigLoadingEnabled,
      final Consumer<SpecConfigBuilder> modifier) {
    return loadConfig(configName, strictConfigLoadingEnabled, true, modifier);
  }

  public static SpecConfigAndParent<? extends SpecConfig> loadRemoteConfig(
      final Map<String, Object> config) {
    final SpecConfigReader reader = new SpecConfigReader();
    if (config.containsKey(SpecConfigReader.PRESET_KEY)) {
      try {
        applyPreset("remote", reader, true, (String) config.get(SpecConfigReader.PRESET_KEY));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    if (config.containsKey(SpecConfigReader.CONFIG_NAME_KEY)) {
      final String configNameKey = (String) config.get(SpecConfigReader.CONFIG_NAME_KEY);
      try {
        processConfig(configNameKey, reader, false, true);
      } catch (IllegalArgumentException exception) {
        LOG.debug(
            "Failed to load base configuration from {}, {}",
            () -> configNameKey,
            exception::getMessage);
      }
    }
    reader.loadFromMap(config, true);
    return reader.build();
  }

  static SpecConfigAndParent<? extends SpecConfig> loadConfig(
      final String configName,
      final boolean strictConfigLoadingEnabled,
      final boolean isIgnoreUnknownConfigItems,
      final Consumer<SpecConfigBuilder> modifier) {
    return loadConfig(
        configName,
        strictConfigLoadingEnabled,
        isIgnoreUnknownConfigItems,
        modifier,
        Collections.emptyMap());
  }

  static SpecConfigAndParent<? extends SpecConfig> loadConfig(
      final String configName,
      final boolean strictConfigLoadingEnabled,
      final boolean isIgnoreUnknownConfigItems,
      final Consumer<SpecConfigBuilder> modifier,
      final Map<String, Object> configOverrides) {
    final SpecConfigReader reader = new SpecConfigReader();
    processConfig(configName, reader, strictConfigLoadingEnabled, isIgnoreUnknownConfigItems);
    return reader.build(
        builder -> {
          modifier.accept(builder);
          // apply any config overrides last
          if (!configOverrides.isEmpty()) {
            reader.loadFromMap(configOverrides, isIgnoreUnknownConfigItems);
          }
        });
  }

  // A little extra configuration is able to be loaded from builtin configs.
  // isIgnoreUnknownConfigItems
  //   - if config in source has entries we don't need, we will silently ignore them
  // isFailRatherThanDefaultFromBuiltin
  //   - if configuration requires a key and it's not specified, fail rather than default it.
  // Presets will be loaded if specified also, this happens after defaulting from config_name
  private static void processConfig(
      final String source,
      final SpecConfigReader reader,
      final boolean isFailRatherThanDefaultFromBuiltin,
      final boolean isIgnoreUnknownConfigItems) {
    try {
      final Map<String, Object> configValues = readConfigToMap(source, reader);

      if (!BUILTIN_NETWORKS.contains(source)
          && configValues.containsKey(SpecConfigReader.CONFIG_NAME_KEY)
          && !isFailRatherThanDefaultFromBuiltin) {
        final String builtinConfigName =
            (String) configValues.get(SpecConfigReader.CONFIG_NAME_KEY);
        if (BUILTIN_NETWORKS.contains(builtinConfigName)) {
          final Map<String, Object> builtinConfig = readConfigToMap(builtinConfigName, reader);
          boolean firstError = true;
          for (final String entry : builtinConfig.keySet()) {
            if (!configValues.containsKey(entry)) {
              if (firstError) {
                firstError = false;
                LOG.warn(
                    "Mapping missing values from {} with our builtin config for {}",
                    source,
                    builtinConfigName);
              }
              LOG.warn("Defaulting {}", entry);
              configValues.put(entry, builtinConfig.get(entry));
            }
          }
        } else {
          LOG.debug(
              "Skipping defaulting config from CONFIG_NAME {}, as this was not found to be builtin",
              builtinConfigName);
        }
      }
      final Optional<String> maybePreset =
          Optional.ofNullable((String) configValues.get(SpecConfigReader.PRESET_KEY));

      // Legacy config files won't have a preset field
      if (maybePreset.isPresent()) {
        final String preset = maybePreset.get();
        applyPreset(source, reader, isIgnoreUnknownConfigItems, preset);
      }
      reader.loadFromMap(configValues, isIgnoreUnknownConfigItems);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Unable to load configuration for network \"" + source + "\": " + e.getMessage(), e);
    }
  }

  private static Map<String, Object> readConfigToMap(
      final String source, final SpecConfigReader reader) {
    try (final InputStream configFile = loadConfigurationFile(source)) {
      return reader.readValues(configFile);
    } catch (IOException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unable to load configuration source \"" + source + "\": " + e.getMessage(), e);
    }
  }

  private static void applyPreset(
      final String source,
      final SpecConfigReader reader,
      final boolean ignoreUnknownConfigItems,
      final String preset)
      throws IOException {
    for (String resource : AVAILABLE_PRESETS) {
      try (final InputStream inputStream = loadPreset(preset, resource).orElse(null)) {
        if (inputStream != null) {
          reader.readAndApply(inputStream, ignoreUnknownConfigItems);
        } else if (resource.equals("phase0")) {
          throw new FileNotFoundException(
              String.format(
                  "Could not load spec config preset '%s' specified in config '%s'",
                  preset, source));
        }
      }
    }
  }

  private static InputStream loadConfigurationFile(final String source) throws IOException {
    if (source.equals(EPHEMERY.configName())) {
      return getConfigLoader()
          .load(EPHEMERY_CONFIG_URL)
          .orElseThrow(
              () -> new FileNotFoundException("Could not load spec config from " + source));
    }
    return getConfigLoader()
        .load(source, CONFIG_PATH + source + ".yaml")
        .orElseThrow(() -> new FileNotFoundException("Could not load spec config from " + source));
  }

  private static Optional<InputStream> loadPreset(final String preset, final String resource)
      throws IOException {
    return getPresetLoader()
        .load(
            PRESET_PATH + preset + "/" + resource + ".yaml",
            PRESET_PATH + preset + "/" + resource + ".yml");
  }

  private static ResourceLoader getConfigLoader() {
    return ResourceLoader.classpathUrlOrFile(
        SpecConfig.class,
        enumerateAvailableConfigResources(),
        s -> s.endsWith(".yaml") || s.endsWith(".yml"));
  }

  private static ResourceLoader getPresetLoader() {
    return ResourceLoader.classpathUrlOrFile(
        SpecConfig.class,
        enumerateAvailablePresetResources(),
        s -> s.endsWith(".yaml") || s.endsWith(".yml"));
  }

  private static List<String> enumerateAvailableConfigResources() {
    return Arrays.stream(Eth2Network.values())
        .map(Eth2Network::configName)
        .map(s -> CONFIG_PATH + s + ".yaml")
        .toList();
  }

  private static List<String> enumerateAvailablePresetResources() {
    return Arrays.stream(Eth2Presets.values())
        .map(Eth2Presets::presetName)
        .flatMap(
            s -> AVAILABLE_PRESETS.stream().map(preset -> PRESET_PATH + s + "/" + preset + ".yaml"))
        .toList();
  }
}
