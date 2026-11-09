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

public class SeparateServiceDataDirLayout implements DataDirLayout {
  static final String BEACON_DATA_DIR_NAME = "beacon";
  static final String VALIDATOR_DATA_DIR_NAME = "validator";
  static final String BOOTNODE_DATA_DIR_NAME = "bootnode";
  static final String DEBUG_DIR_NAME = "debug";
  private final Path beaconNodeDataDir;
  private final Path bootnodeDataDir;
  private final Path validatorDataDir;
  private final Path debugDataDir;
  private final boolean debugDataDumpingEnabled;

  public SeparateServiceDataDirLayout(
      final Path baseDir,
      final Optional<Path> beaconDataDirectory,
      final Optional<Path> bootnodeDataDirectory,
      final Optional<Path> validatorDataDirectory,
      final boolean debugDataDumpingEnabled) {
    beaconNodeDataDir = beaconDataDirectory.orElseGet(() -> baseDir.resolve(BEACON_DATA_DIR_NAME));
    bootnodeDataDir =
        bootnodeDataDirectory.orElseGet(() -> baseDir.resolve(BOOTNODE_DATA_DIR_NAME));
    validatorDataDir =
        validatorDataDirectory.orElseGet(() -> baseDir.resolve(VALIDATOR_DATA_DIR_NAME));
    debugDataDir = baseDir.resolve(DEBUG_DIR_NAME);
    this.debugDataDumpingEnabled = debugDataDumpingEnabled;
  }

  @Override
  public Path getBeaconDataDirectory() {
    return beaconNodeDataDir;
  }

  @Override
  public Path getBootnodeDataDirectory() {
    return bootnodeDataDir;
  }

  @Override
  public Path getValidatorDataDirectory() {
    return validatorDataDir;
  }

  @Override
  public Path getDebugDataDirectory() {
    return debugDataDir;
  }

  @Override
  public boolean isDebugDataDumpingEnabled() {
    return debugDataDumpingEnabled;
  }
}
