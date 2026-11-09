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

package tech.pegasys.teku.service.serviceutils.layout;

import java.nio.file.Path;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class DataConfig {

  public static final Path DEFAULT_DATA_PATH = Path.of(VersionProvider.defaultStoragePath());
  public static final boolean DEFAULT_DEBUG_DATA_DUMPING_ENABLED = false;

  private final Path dataBasePath;
  private final Optional<Path> beaconDataPath;
  private final Optional<Path> bootnodeDataPath;
  private final Optional<Path> validatorDataPath;
  private final boolean debugDataDumpingEnabled;

  private DataConfig(
      final Path dataBasePath,
      final Optional<Path> beaconDataPath,
      final Optional<Path> bootnodeDataPath,
      final Optional<Path> validatorDataPath,
      final boolean debugDataDumpingEnabled) {
    this.dataBasePath = dataBasePath;
    this.beaconDataPath = beaconDataPath;
    this.bootnodeDataPath = bootnodeDataPath;
    this.validatorDataPath = validatorDataPath;
    this.debugDataDumpingEnabled = debugDataDumpingEnabled;
  }

  public static DataConfig.Builder builder() {
    return new Builder();
  }

  public Path getDataBasePath() {
    return dataBasePath;
  }

  public Optional<Path> getBeaconDataPath() {
    return beaconDataPath;
  }

  public Optional<Path> getBootnodeDataPath() {
    return bootnodeDataPath;
  }

  public Optional<Path> getValidatorDataPath() {
    return validatorDataPath;
  }

  public boolean isDebugDataDumpingEnabled() {
    return debugDataDumpingEnabled;
  }

  public static final class Builder {

    private Path dataBasePath = DEFAULT_DATA_PATH;
    private Optional<Path> beaconDataPath = Optional.empty();
    private Optional<Path> bootnodeDataPath = Optional.empty();
    private Optional<Path> validatorDataPath = Optional.empty();
    private boolean debugDataDumpingEnabled = DEFAULT_DEBUG_DATA_DUMPING_ENABLED;

    private Builder() {}

    public Builder dataBasePath(final Path dataBasePath) {
      this.dataBasePath = dataBasePath;
      return this;
    }

    public Builder beaconDataPath(final Path beaconDataPath) {
      this.beaconDataPath = Optional.ofNullable(beaconDataPath);
      return this;
    }

    public Builder bootnodeDataPath(final Path bootnodeDataPath) {
      this.bootnodeDataPath = Optional.ofNullable(bootnodeDataPath);
      return this;
    }

    public Builder validatorDataPath(final Path validatorDataPath) {
      this.validatorDataPath = Optional.ofNullable(validatorDataPath);
      return this;
    }

    public Builder debugDataDumpingEnabled(final boolean debugDataDumpingEnabled) {
      this.debugDataDumpingEnabled = debugDataDumpingEnabled;
      return this;
    }

    public DataConfig build() {
      if (dataBasePath == null) {
        throw new InvalidConfigurationException("data-base-path must be specified");
      }
      return new DataConfig(
          dataBasePath,
          beaconDataPath,
          bootnodeDataPath,
          validatorDataPath,
          debugDataDumpingEnabled);
    }
  }
}
