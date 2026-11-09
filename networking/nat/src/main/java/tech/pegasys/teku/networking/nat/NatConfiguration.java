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

package tech.pegasys.teku.networking.nat;

public class NatConfiguration {
  private final NatMethod natMethod;

  private NatConfiguration(final NatMethod natMethod) {
    this.natMethod = natMethod;
  }

  public NatMethod getNatMethod() {
    return natMethod;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    public Builder() {}

    private NatMethod natMethod = NatMethod.NONE;

    public Builder natMethod(final NatMethod natMethod) {
      this.natMethod = natMethod;
      return this;
    }

    public NatConfiguration build() {
      return new NatConfiguration(natMethod);
    }
  }
}
