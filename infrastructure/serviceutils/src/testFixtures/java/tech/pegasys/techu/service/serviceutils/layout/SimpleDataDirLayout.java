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

package tech.pegasys.techu.service.serviceutils.layout;

import java.nio.file.Path;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

public class SimpleDataDirLayout implements DataDirLayout {
  private final Path path;

  public SimpleDataDirLayout(final Path path) {
    this.path = path;
  }

  @Override
  public Path getBeaconDataDirectory() {
    return path;
  }

  @Override
  public Path getBootnodeDataDirectory() {
    return path;
  }

  @Override
  public Path getValidatorDataDirectory() {
    return path;
  }

  @Override
  public Path getDebugDataDirectory() {
    return path;
  }

  @Override
  public boolean isDebugDataDumpingEnabled() {
    return false;
  }
}
