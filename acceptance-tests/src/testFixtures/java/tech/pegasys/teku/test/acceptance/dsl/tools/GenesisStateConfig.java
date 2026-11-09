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

package tech.pegasys.teku.test.acceptance.dsl.tools;

import java.util.Objects;

public class GenesisStateConfig {
  private static final String DEFAULT_LOCATION = "/config/genesis-state.bin";
  private final int numValidators;
  private final long genesisTime;

  private GenesisStateConfig(final int numValidators, final long genesisTime) {
    this.numValidators = numValidators;
    this.genesisTime = genesisTime;
  }

  public static GenesisStateConfig create(final int numValidators) {
    return create(numValidators, System.currentTimeMillis() / 1000);
  }

  public static GenesisStateConfig create(final int numValidators, final long genesisTime) {
    return new GenesisStateConfig(numValidators, genesisTime);
  }

  public int getNumValidators() {
    return numValidators;
  }

  public long getGenesisTime() {
    return genesisTime;
  }

  public String getPath() {
    return DEFAULT_LOCATION;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof GenesisStateConfig)) {
      return false;
    }
    final GenesisStateConfig that = (GenesisStateConfig) o;
    return numValidators == that.numValidators && genesisTime == that.genesisTime;
  }

  @Override
  public int hashCode() {
    return Objects.hash(numValidators, genesisTime);
  }
}
