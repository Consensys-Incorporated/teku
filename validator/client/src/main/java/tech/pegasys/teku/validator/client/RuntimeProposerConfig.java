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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;

import com.google.common.base.Preconditions;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderEntry;

public class RuntimeProposerConfig {
  private final Optional<Path> storagePath;
  private static final Logger LOG = LogManager.getLogger();

  private static final StringValueTypeDefinition<BLSPublicKey> PUBKEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .formatter(BLSPublicKey::toString)
          .parser(BLSPublicKey::fromHexString)
          .format("byte")
          .build();
  private static final DeserializableTypeDefinition<RuntimeConfig> CONFIG_TYPE =
      DeserializableTypeDefinition.object(RuntimeConfig.class, RuntimeConfigBuilder.class)
          .initializer(RuntimeConfigBuilder::new)
          .finisher(RuntimeConfigBuilder::build)
          .name("RuntimeProposerConfig")
          .withOptionalField(
              "fee_recipient",
              ETH1ADDRESS_TYPE,
              RuntimeConfig::getFeeRecipient,
              RuntimeConfigBuilder::feeRecipient)
          .withOptionalField(
              "gas_limit",
              CoreTypes.UINT64_TYPE,
              RuntimeConfig::getGasLimit,
              RuntimeConfigBuilder::gasLimit)
          .withOptionalField(
              "builder_config",
              ValidatorTypes.BUILDER_CONFIG_TYPE,
              RuntimeConfig::getBuilderConfig,
              RuntimeConfigBuilder::builderConfig)
          .build();
  private static final DeserializableTypeDefinition<Map<BLSPublicKey, RuntimeConfig>>
      CONFIG_MAP_TYPE =
          DeserializableTypeDefinition.mapOf(PUBKEY_TYPE, CONFIG_TYPE, ConcurrentHashMap::new);

  private final Map<BLSPublicKey, RuntimeConfig> proposerConfigMap = new ConcurrentHashMap<>();

  public RuntimeProposerConfig(final Optional<Path> storagePath) {
    this.storagePath = storagePath;
    storagePath.ifPresent(
        path -> {
          if (path.toFile().exists()) {
            try (InputStream inputStream = new FileInputStream(path.toFile())) {
              proposerConfigMap.putAll(JsonUtil.parse(inputStream, CONFIG_MAP_TYPE));
            } catch (IOException e) {
              throw new IllegalStateException("Failed to parse file: " + path.toAbsolutePath(), e);
            }
          }
        });
  }

  public Optional<Eth1Address> getEth1AddressForPubKey(final BLSPublicKey publicKey) {
    return getProposerConfig(publicKey).flatMap(RuntimeConfig::getFeeRecipient);
  }

  public Optional<UInt64> getGasLimitForPubKey(final BLSPublicKey publicKey) {
    return getProposerConfig(publicKey).flatMap(RuntimeConfig::getGasLimit);
  }

  synchronized void updateFeeRecipient(
      final BLSPublicKey publicKey, final Eth1Address eth1Address) {
    Preconditions.checkNotNull(eth1Address, "should delete rather than update to null");
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isEmpty()) {
      proposerConfigMap.put(
          publicKey, new RuntimeConfigBuilder().feeRecipient(Optional.of(eth1Address)).build());
    } else {
      RuntimeConfigBuilder configBuilder = new RuntimeConfigBuilder(currentConfig.get());
      configBuilder.feeRecipient(Optional.of(eth1Address));
      updateEntry(publicKey, configBuilder.build());
    }
    storagePath.ifPresent(this::save);
  }

  synchronized void updateGasLimit(final BLSPublicKey publicKey, final UInt64 gasLimit) {
    Preconditions.checkNotNull(gasLimit, "should delete rather than update to null");
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isEmpty()) {
      proposerConfigMap.put(
          publicKey, new RuntimeConfigBuilder().gasLimit(Optional.of(gasLimit)).build());
    } else {
      RuntimeConfigBuilder configBuilder = new RuntimeConfigBuilder(currentConfig.get());
      configBuilder.gasLimit(Optional.of(gasLimit));
      updateEntry(publicKey, configBuilder.build());
    }
    storagePath.ifPresent(this::save);
  }

  private synchronized void updateEntry(final BLSPublicKey publicKey, final RuntimeConfig config) {
    if (config.isEmpty()) {
      proposerConfigMap.remove(publicKey);
    } else {
      proposerConfigMap.put(publicKey, config);
    }
  }

  synchronized void deleteFeeRecipient(final BLSPublicKey publicKey) {
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isPresent()) {
      RuntimeConfigBuilder builder = new RuntimeConfigBuilder(currentConfig.get());
      builder.feeRecipient(Optional.empty());
      updateEntry(publicKey, builder.build());
      storagePath.ifPresent(this::save);
    }
  }

  synchronized void deleteGasLimit(final BLSPublicKey publicKey) {
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isPresent()) {
      RuntimeConfigBuilder builder = new RuntimeConfigBuilder(currentConfig.get());
      builder.gasLimit(Optional.empty());
      updateEntry(publicKey, builder.build());
      storagePath.ifPresent(this::save);
    }
  }

  public Optional<RuntimeConfig> getProposerConfig(final BLSPublicKey publicKey) {
    return Optional.ofNullable(proposerConfigMap.get(publicKey));
  }

  private void save(final Path path) {
    try (OutputStream writer = new FileOutputStream(path.toFile(), false)) {
      JsonUtil.serializeToBytes(proposerConfigMap, CONFIG_MAP_TYPE, writer);
    } catch (IOException e) {
      LOG.error("Failed to store file: " + path.toAbsolutePath(), e);
    }
  }

  static class RuntimeConfig extends ProposerConfig.Config {

    private final Optional<Eth1Address> feeRecipient;
    private final Optional<UInt64> gasLimit;
    private final Optional<BuilderConfig> builderConfig;
    private final boolean isEmpty;

    public RuntimeConfig(
        final Optional<Eth1Address> feeRecipient,
        final Optional<UInt64> gasLimit,
        final Optional<BuilderConfig> builderConfig) {
      super(feeRecipient.orElse(null), createBuilder(gasLimit, builderConfig));
      this.feeRecipient = feeRecipient;
      this.gasLimit = gasLimit;
      this.builderConfig = builderConfig;
      isEmpty = feeRecipient.isEmpty() && gasLimit.isEmpty() && builderConfig.isEmpty();
    }

    private static ProposerConfig.BuilderConfig createBuilder(
        final Optional<UInt64> gasLimit, final Optional<BuilderConfig> builderConfig) {
      if (gasLimit.isPresent() || builderConfig.isPresent()) {
        final Optional<Map<String, ProposerConfig.BuilderOverrides>> urls =
            builderConfig
                .flatMap(BuilderConfig::builders)
                .map(
                    builders ->
                        builders.stream()
                            .collect(
                                Collectors.toMap(
                                    BuilderEntry::url,
                                    builderEntry ->
                                        new ProposerConfig.BuilderOverrides(
                                            builderEntry.authData().orElse(null),
                                            builderEntry.builderPubkeys().orElse(null),
                                            builderEntry.minBid().orElse(null),
                                            builderEntry.builderBoostFactor().orElse(null),
                                            builderEntry.maxExecutionPayment().orElse(null)))));
        return new ProposerConfig.BuilderConfig(
            null,
            gasLimit.orElse(null),
            null,
            builderConfig.flatMap(BuilderConfig::minBid).orElse(null),
            builderConfig.flatMap(BuilderConfig::builderBoostFactor).orElse(null),
            urls.orElse(null));
      } else {
        return null;
      }
    }

    @Override
    public Optional<Eth1Address> getFeeRecipient() {
      return feeRecipient;
    }

    @Override
    public Optional<UInt64> getGasLimit() {
      return gasLimit;
    }

    public Optional<BuilderConfig> getBuilderConfig() {
      return builderConfig;
    }

    public boolean isEmpty() {
      return isEmpty;
    }
  }

  static class RuntimeConfigBuilder {
    private Optional<Eth1Address> feeRecipient = Optional.empty();
    private Optional<UInt64> gasLimit = Optional.empty();
    private Optional<BuilderConfig> builderConfig = Optional.empty();

    public RuntimeConfigBuilder() {}

    public RuntimeConfigBuilder(final RuntimeConfig currentConfig) {
      feeRecipient = currentConfig.getFeeRecipient();
      gasLimit = currentConfig.getGasLimit();
      builderConfig = currentConfig.getBuilderConfig();
    }

    public RuntimeConfigBuilder feeRecipient(final Optional<Eth1Address> feeRecipient) {
      this.feeRecipient = feeRecipient;
      return this;
    }

    public RuntimeConfigBuilder gasLimit(final Optional<UInt64> gasLimit) {
      this.gasLimit = gasLimit;
      return this;
    }

    public RuntimeConfigBuilder builderConfig(final Optional<BuilderConfig> builderConfig) {
      this.builderConfig = builderConfig;
      return this;
    }

    public RuntimeConfig build() {
      return new RuntimeConfig(feeRecipient, gasLimit, builderConfig);
    }
  }
}
