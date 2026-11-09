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

public enum NatServiceType {
  TEKU_DISCOVERY("teku_discovery"),
  TEKU_P2P("teku_p2p");
  private final String value;

  NatServiceType(final String value) {
    this.value = value;
  }

  public static NatServiceType fromString(final String natServiceTypeName) {
    for (final NatServiceType mode : NatServiceType.values()) {
      if (mode.getValue().equalsIgnoreCase(natServiceTypeName)) {
        return mode;
      }
    }
    throw new IllegalStateException(
        String.format("Invalid NAT service type provided: %s", natServiceTypeName));
  }

  public String getValue() {
    return value;
  }
}
