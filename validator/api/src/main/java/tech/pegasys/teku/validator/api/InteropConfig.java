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

package tech.pegasys.teku.validator.api;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;

public class InteropConfig {

  public static final int DEFAULT_INTEROP_GENESIS_TIME = 0;
  public static final int DEFAULT_INTEROP_NUMBER_OF_VALIDATORS = 64;

  private final Integer interopGenesisTime;
  private final Optional<Path> interopGenesisPayloadHeader;
  private final int interopOwnedValidatorStartIndex;
  private final int interopOwnedValidatorCount;
  private final int interopNumberOfValidators;
  private final boolean interopEnabled;

  private InteropConfig(
      final Integer interopGenesisTime,
      final Optional<Path> interopGenesisPayloadHeader,
      final int interopOwnedValidatorStartIndex,
      final int interopOwnedValidatorCount,
      final int interopNumberOfValidators,
      final boolean interopEnabled) {
    this.interopGenesisTime = interopGenesisTime;
    this.interopGenesisPayloadHeader = interopGenesisPayloadHeader;
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    this.interopNumberOfValidators = interopNumberOfValidators;
    this.interopEnabled = interopEnabled;
  }

  public static InteropConfigBuilder builder() {
    return new InteropConfigBuilder();
  }

  public Integer getInteropGenesisTime() {
    return interopGenesisTime;
  }

  public Optional<Path> getInteropGenesisPayloadHeader() {
    return interopGenesisPayloadHeader;
  }

  public int getInteropOwnedValidatorStartIndex() {
    return interopOwnedValidatorStartIndex;
  }

  public int getInteropOwnedValidatorCount() {
    return interopOwnedValidatorCount;
  }

  public int getInteropNumberOfValidators() {
    return interopNumberOfValidators;
  }

  public boolean isInteropEnabled() {
    return interopEnabled;
  }

  public static final class InteropConfigBuilder {

    private Integer interopGenesisTime = DEFAULT_INTEROP_GENESIS_TIME;
    private Optional<Path> interopGenesisPayloadHeader = Optional.empty();
    private int interopOwnedValidatorStartIndex;
    private int interopOwnedValidatorCount;
    private int interopNumberOfValidators = DEFAULT_INTEROP_NUMBER_OF_VALIDATORS;
    private boolean interopEnabled = false;
    private Spec spec;

    private InteropConfigBuilder() {}

    public InteropConfigBuilder interopGenesisTime(final Integer interopGenesisTime) {
      if (interopGenesisTime < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid interopGenesisTime: %d", interopGenesisTime));
      }
      this.interopGenesisTime = interopGenesisTime;
      return this;
    }

    public InteropConfigBuilder interopGenesisPayloadHeader(
        final Path interopGenesisPayloadHeader) {
      this.interopGenesisPayloadHeader = Optional.ofNullable(interopGenesisPayloadHeader);
      return this;
    }

    public InteropConfigBuilder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public InteropConfigBuilder interopOwnedValidatorStartIndex(
        final int interopOwnedValidatorStartIndex) {
      if (interopOwnedValidatorStartIndex < 0) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid interopOwnedValidatorStartIndex: %d", interopOwnedValidatorStartIndex));
      }
      this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
      return this;
    }

    public InteropConfigBuilder interopOwnedValidatorCount(final int interopOwnedValidatorCount) {
      if (interopOwnedValidatorCount < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid interopOwnedValidatorCount: %d", interopOwnedValidatorCount));
      }
      this.interopOwnedValidatorCount = interopOwnedValidatorCount;
      return this;
    }

    public InteropConfigBuilder interopNumberOfValidators(final int interopNumberOfValidators) {
      if (interopNumberOfValidators < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid interopNumberOfValidators: %d", interopNumberOfValidators));
      }
      this.interopNumberOfValidators = interopNumberOfValidators;
      return this;
    }

    public InteropConfigBuilder interopEnabled(final boolean interopEnabled) {
      this.interopEnabled = interopEnabled;
      return this;
    }

    public InteropConfig build() {
      initMissingDefaults();
      validate();
      return new InteropConfig(
          interopGenesisTime,
          interopGenesisPayloadHeader,
          interopOwnedValidatorStartIndex,
          interopOwnedValidatorCount,
          interopNumberOfValidators,
          interopEnabled);
    }

    private void initMissingDefaults() {
      if (interopEnabled && interopGenesisTime == 0) {
        interopGenesisTime = Math.toIntExact((System.currentTimeMillis() / 1000) + 5);
      }
    }

    private void validate() throws IllegalArgumentException {
      checkNotNull(spec);
      final SpecConfig genesisSpecConfig = spec.getGenesisSpecConfig();
      if (interopNumberOfValidators < genesisSpecConfig.getSlotsPerEpoch()) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid configuration. Interop number of validators [%d] must be greater than or equal to [%d]",
                interopNumberOfValidators, genesisSpecConfig.getSlotsPerEpoch()));
      }
    }
  }
}
