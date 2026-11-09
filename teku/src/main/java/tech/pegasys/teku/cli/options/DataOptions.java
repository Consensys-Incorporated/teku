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

import static tech.pegasys.teku.service.serviceutils.layout.DataConfig.DEFAULT_DATA_PATH;

import java.nio.file.Path;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;

public abstract class DataOptions {

  @Option(
      names = {"--data-base-path", "--data-path"},
      paramLabel = "<FILENAME>",
      description = "Path to the base directory for storage",
      arity = "1")
  private Path dataBasePath = DEFAULT_DATA_PATH;

  public DataConfig getDataConfig() {
    return configureDataConfig(DataConfig.builder()).build();
  }

  public String getDataPath() {
    return getDataConfig().getDataBasePath().toString();
  }

  public void configure(final TekuConfiguration.Builder builder) {
    builder.data(this::configureDataConfig);
  }

  protected DataConfig.Builder configureDataConfig(final DataConfig.Builder config) {
    return config.dataBasePath(dataBasePath);
  }
}
