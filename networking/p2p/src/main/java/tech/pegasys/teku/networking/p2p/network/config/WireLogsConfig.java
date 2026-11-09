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

package tech.pegasys.teku.networking.p2p.network.config;

import static com.google.common.base.Preconditions.checkNotNull;

public class WireLogsConfig {
  private final boolean logWireCipher;
  private final boolean logWirePlain;
  private final boolean logWireMuxFrames;
  private final boolean logWireGossip;

  private WireLogsConfig(
      final boolean logWireCipher,
      final boolean logWirePlain,
      final boolean logWireMuxFrames,
      final boolean logWireGossip) {
    this.logWireCipher = logWireCipher;
    this.logWirePlain = logWirePlain;
    this.logWireMuxFrames = logWireMuxFrames;
    this.logWireGossip = logWireGossip;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static WireLogsConfig createDefault() {
    return builder().build();
  }

  public boolean isLogWireCipher() {
    return logWireCipher;
  }

  public boolean isLogWirePlain() {
    return logWirePlain;
  }

  public boolean isLogWireMuxFrames() {
    return logWireMuxFrames;
  }

  public boolean isLogWireGossip() {
    return logWireGossip;
  }

  public static class Builder {
    private Boolean logWireCipher = false;
    private Boolean logWirePlain = false;
    private Boolean logWireMuxFrames = false;
    private Boolean logWireGossip = false;

    private Builder() {}

    public WireLogsConfig build() {
      return new WireLogsConfig(logWireCipher, logWirePlain, logWireMuxFrames, logWireGossip);
    }

    public Builder logWireCipher(final Boolean logWireCipher) {
      checkNotNull(logWireCipher);
      this.logWireCipher = logWireCipher;
      return this;
    }

    public Builder logWirePlain(final Boolean logWirePlain) {
      checkNotNull(logWirePlain);
      this.logWirePlain = logWirePlain;
      return this;
    }

    public Builder logWireMuxFrames(final Boolean logWireMuxFrames) {
      checkNotNull(logWireMuxFrames);
      this.logWireMuxFrames = logWireMuxFrames;
      return this;
    }

    public Builder logWireGossip(final Boolean logWireGossip) {
      checkNotNull(logWireGossip);
      this.logWireGossip = logWireGossip;
      return this;
    }
  }
}
