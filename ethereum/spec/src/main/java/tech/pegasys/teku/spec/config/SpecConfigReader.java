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

import static tech.pegasys.teku.spec.config.SpecConfigFormatter.camelToSnakeCase;

import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.builder.AltairBuilder;
import tech.pegasys.teku.spec.config.builder.BellatrixBuilder;
import tech.pegasys.teku.spec.config.builder.CapellaBuilder;
import tech.pegasys.teku.spec.config.builder.DenebBuilder;
import tech.pegasys.teku.spec.config.builder.ElectraBuilder;
import tech.pegasys.teku.spec.config.builder.FuluBuilder;
import tech.pegasys.teku.spec.config.builder.GloasBuilder;
import tech.pegasys.teku.spec.config.builder.HezeBuilder;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public class SpecConfigReader {
  private static final Logger LOG = LogManager.getLogger();
  public static final String PRESET_KEY = "PRESET_BASE";
  public static final String CONFIG_NAME_KEY = "CONFIG_NAME";
  private static final ImmutableSet<String> KEYS_TO_IGNORE =
      ImmutableSet.of(
          PRESET_KEY,
          CONFIG_NAME_KEY,
          // Unsupported, upcoming fork-related keys
          "SHARDING_FORK_VERSION",
          "SHARDING_FORK_EPOCH",
          "EIP6110_FORK_VERSION",
          "EIP6110_FORK_EPOCH",
          "WHISK_FORK_VERSION",
          "WHISK_FORK_EPOCH",
          // Old merge config item which is no longer used, ignore for backwards compatibility
          "TRANSITION_TOTAL_DIFFICULTY",
          // Deprecated fields
          "GOSSIP_MAX_SIZE_BELLATRIX",
          "MAX_CHUNK_SIZE_BELLATRIX",
          "MAX_CHUNK_SIZE",
          // Deprecated networking fields not used by Teku
          "RESP_TIMEOUT",
          "TTFB_TIMEOUT");
  private static final ImmutableSet<String> CONSTANT_KEYS =
      ImmutableSet.of(
          // Phase0 constants which may exist in legacy config files, but should now be ignored
          "BLS_WITHDRAWAL_PREFIX",
          "TARGET_AGGREGATORS_PER_COMMITTEE",
          "EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION",
          "DOMAIN_BEACON_PROPOSER",
          "DOMAIN_BEACON_ATTESTER",
          "DOMAIN_RANDAO",
          "DOMAIN_DEPOSIT",
          "DOMAIN_VOLUNTARY_EXIT",
          "DOMAIN_SELECTION_PROOF",
          "DOMAIN_AGGREGATE_AND_PROOF",
          // Altair constants
          "DOMAIN_SYNC_COMMITTEE",
          "DOMAIN_CONTRIBUTION_AND_PROOF",
          "TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE",
          "DOMAIN_SYNC_COMMITTEE_SELECTION_PROOF",
          "SYNC_COMMITTEE_SUBNET_COUNT");

  private final ImmutableMap<Class<?>, Function<Object, ?>> parsers =
      ImmutableMap.<Class<?>, Function<Object, ?>>builder()
          .put(Integer.TYPE, this::parseInt)
          .put(Integer.class, this::parseInt)
          .put(Long.TYPE, this::parseLong)
          .put(Long.class, this::parseLong)
          .put(Boolean.TYPE, fromString(Boolean::valueOf))
          .put(Boolean.class, fromString(Boolean::valueOf))
          .put(UInt64.class, fromString(UInt64::valueOf))
          .put(UInt256.class, fromString(str -> UInt256.valueOf(new BigInteger(str))))
          .put(String.class, Function.identity())
          .put(Bytes.class, fromString(Bytes::fromHexString))
          .put(Bytes4.class, fromString(Bytes4::fromHexString))
          .put(Bytes32.class, fromString(Bytes32::fromHexStringStrict))
          .put(Eth1Address.class, fromString(Eth1Address::fromHexString))
          .build();

  /** Parsers for config items which cannot be resolved from the setter type alone. */
  private final ImmutableMap<String, Function<Object, ?>> parsersByKey =
      ImmutableMap.<String, Function<Object, ?>>builder()
          .put("BLOB_SCHEDULE", this::blobScheduleFromList)
          .put("GAS_LIMIT_SCHEDULE", this::gasLimitScheduleFromList)
          .build();

  final SpecConfigBuilder configBuilder = SpecConfig.builder();

  SpecConfigAndParent<? extends SpecConfig> build() {
    return configBuilder.build();
  }

  SpecConfigAndParent<? extends SpecConfig> build(final Consumer<SpecConfigBuilder> modifier) {
    modifier.accept(configBuilder);
    return build();
  }

  /**
   * Reads and processes the resource, returns any referenced "preset" to be processed if a preset
   * field is set
   *
   * @param source The source to read
   * @throws IOException Thrown if an error occurs reading the source
   */
  void readAndApply(final InputStream source, final boolean ignoreUnknownConfigItems)
      throws IOException {
    final Map<String, Object> rawValues = readValues(source);
    loadFromMap(rawValues, ignoreUnknownConfigItems);
  }

  void loadFromMap(final Map<String, Object> rawValues, final boolean ignoreUnknownConfigItems) {
    final Map<String, Object> unprocessedConfig = new HashMap<>(rawValues);
    final Map<String, Object> apiSpecConfig = new HashMap<>(rawValues);
    // Remove any keys that we're ignoring
    KEYS_TO_IGNORE.forEach(
        key -> {
          unprocessedConfig.remove(key);
          if (!key.equals(PRESET_KEY) && !key.equals(CONFIG_NAME_KEY)) {
            apiSpecConfig.remove(key);
          }
        });

    // Process phase0 config
    configBuilder.rawConfig(apiSpecConfig);
    streamConfigSetters(configBuilder.getClass())
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(
                  setter, BuilderSupplier.fromBuilder(configBuilder), constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process altair config
    streamConfigSetters(AltairBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::altairBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process bellatrix config
    streamConfigSetters(BellatrixBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::bellatrixBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process capella config
    streamConfigSetters(CapellaBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::capellaBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process deneb config
    streamConfigSetters(DenebBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::denebBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process electra config
    streamConfigSetters(ElectraBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::electraBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process fulu config
    streamConfigSetters(FuluBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::fuluBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process gloas config
    streamConfigSetters(GloasBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::gloasBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Process heze config
    streamConfigSetters(HezeBuilder.class)
        .forEach(
            setter -> {
              final String constantKey = camelToSnakeCase(setter.getName());
              final Object rawValue = unprocessedConfig.get(constantKey);
              invokeSetter(setter, configBuilder::hezeBuilder, constantKey, rawValue);
              unprocessedConfig.remove(constantKey);
            });

    // Check any constants that have been configured and then ignore
    final Set<String> configuredConstants =
        Sets.intersection(CONSTANT_KEYS, unprocessedConfig.keySet());
    if (configuredConstants.size() > 0) {
      LOG.info(
          "Ignoring non-configurable constants supplied in network configuration: {}",
          String.join(", ", configuredConstants));
    }
    CONSTANT_KEYS.forEach(unprocessedConfig::remove);

    if (unprocessedConfig.size() > 0) {
      final String unknownKeys = String.join(",", unprocessedConfig.keySet());
      if (!ignoreUnknownConfigItems) {
        throw new IllegalArgumentException("Detected unknown spec config entries: " + unknownKeys);
      } else {
        LOG.warn("Ignoring unknown items in network configuration: {}", unknownKeys);
      }
    }
  }

  Map<String, Object> readValues(final InputStream source) throws IOException {
    final YamlConfigReader reader = new YamlConfigReader();
    try {
      return reader.readValues(source);
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("Supplied spec config is empty");
    } catch (RuntimeJsonMappingException e) {
      throw new IllegalArgumentException("Cannot read spec config: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Object blobScheduleFromList(final Object o) {
    final List<BlobScheduleEntry> blobSchedule = new ArrayList<>();
    final List<?> schedule = (List<?>) o;
    for (Object entry : schedule) {
      if (entry instanceof Map) {
        final Map<String, String> data = (Map<String, String>) entry;
        if (!data.containsKey("EPOCH")
            || !data.containsKey("MAX_BLOBS_PER_BLOCK")
            || data.size() != 2) {
          throw new IllegalArgumentException("Map does not look like a blob schedule");
        }
        blobSchedule.add(
            new BlobScheduleEntry(
                UInt64.valueOf(data.get("EPOCH")),
                Integer.parseInt(data.get("MAX_BLOBS_PER_BLOCK"))));

      } else {
        throw new IllegalArgumentException("Could not parse entry blob schedule");
      }
    }
    return blobSchedule;
  }

  @SuppressWarnings("unchecked")
  private Object gasLimitScheduleFromList(final Object o) {
    final List<GasLimitScheduleEntry> gasLimitSchedule = new ArrayList<>();
    final List<?> schedule = (List<?>) o;
    for (Object entry : schedule) {
      if (entry instanceof Map) {
        final Map<String, String> data = (Map<String, String>) entry;
        if (!data.containsKey("EPOCH") || !data.containsKey("GAS_LIMIT") || data.size() != 2) {
          throw new IllegalArgumentException("Map does not look like a gas limit schedule");
        }
        gasLimitSchedule.add(
            new GasLimitScheduleEntry(
                UInt64.valueOf(data.get("EPOCH")), UInt64.valueOf(data.get("GAS_LIMIT"))));

      } else {
        throw new IllegalArgumentException("Could not parse entry gas limit schedule");
      }
    }
    return gasLimitSchedule;
  }

  private Stream<Method> streamConfigSetters(final Class<?> builderClass) {
    // Ignore any setters that aren't for individual config entries
    final Set<String> ignoredSetters = Set.of("rawConfig");

    return Arrays.stream(builderClass.getMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()))
        .filter(m -> m.getReturnType() == builderClass)
        .filter(m -> m.getParameterTypes().length == 1)
        .filter(m -> !ignoredSetters.contains(m.getName()));
  }

  private <T> void invokeSetter(
      final Method setterMethod,
      final BuilderSupplier<T> builder,
      final String constantKey,
      final Object rawValue) {
    if (rawValue == null) {
      return;
    }

    final Class<?> valueType = setterMethod.getParameterTypes()[0];
    final Object value = parseValue(valueType, constantKey, rawValue);
    builder.updateBuilder(
        b -> {
          try {
            setterMethod.invoke(b, value);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private Object parseValue(final Class<?> valueType, final String key, final Object value) {
    final Function<Object, ?> parser =
        parsersByKey.containsKey(key) ? parsersByKey.get(key) : parsers.get(valueType);
    if (parser == null) {
      throw new IllegalStateException("Missing parser for constant type: " + valueType);
    }
    try {
      return parser.apply(value);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Failed to parse value for constant " + key + ": '" + value + "'", e);
    }
  }

  private int parseInt(final Object input) {
    final String stringValue = input.toString();
    final int value;
    if (input instanceof Integer) {
      value = (Integer) input;
    } else if (stringValue.startsWith("0x")) {
      value =
          Bytes.fromHexString(stringValue)
              .toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN)
              .intValueExact();
    } else {
      value = Integer.parseInt(stringValue, 10);
    }

    // Validate
    if (value < 0) {
      throw new IllegalArgumentException("Integer values must be positive");
    }

    return value;
  }

  private Long parseLong(final Object rawValue) {
    final long value = Long.valueOf(rawValue.toString(), 10);
    if (value < 0) {
      throw new IllegalArgumentException("Long values must be positive");
    }

    return value;
  }

  private <T> Function<Object, T> fromString(final Function<String, T> function) {
    return value -> function.apply(value.toString());
  }

  private interface BuilderSupplier<TBuilder> {
    static <T> BuilderSupplier<T> fromBuilder(final T builder) {
      return (consumer) -> consumer.accept(builder);
    }

    void updateBuilder(final Consumer<TBuilder> consumer);
  }
}
