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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.DefaultRpcPayloadEncoder;

public class RpcPayloadEncoders {

  private final Map<SszSchema<?>, RpcPayloadEncoder<?>> encoders;
  private final Function<SszSchema<?>, RpcPayloadEncoder<?>> defaultEncoderProvider;

  private RpcPayloadEncoders(
      final Map<SszSchema<?>, RpcPayloadEncoder<?>> encoders,
      final Function<SszSchema<?>, RpcPayloadEncoder<?>> defaultEncoderProvider) {
    this.encoders = encoders;
    this.defaultEncoderProvider = defaultEncoderProvider;
  }

  public static RpcPayloadEncoders createSszEncoders() {
    return RpcPayloadEncoders.builder()
        .defaultEncoderProvider(DefaultRpcPayloadEncoder::new)
        .build();
  }

  public static RpcPayloadEncoders.Builder builder() {
    return new RpcPayloadEncoders.Builder();
  }

  @SuppressWarnings("unchecked")
  public <T extends SszData> RpcPayloadEncoder<T> getEncoder(final SszSchema<T> clazz) {
    RpcPayloadEncoder<?> encoder = encoders.get(clazz);
    return (RpcPayloadEncoder<T>) (encoder != null ? encoder : defaultEncoderProvider.apply(clazz));
  }

  public static class Builder {
    private final Map<SszSchema<?>, RpcPayloadEncoder<?>> encoders = new HashMap<>();
    private Function<SszSchema<?>, RpcPayloadEncoder<?>> defaultEncoderProvider;

    private Builder() {}

    public <T> Builder withEncoder(final SszSchema<?> type, final RpcPayloadEncoder<T> encoder) {
      encoders.put(type, encoder);
      return this;
    }

    public Builder defaultEncoderProvider(
        final Function<SszSchema<?>, RpcPayloadEncoder<?>> defaultEncoderProvider) {
      this.defaultEncoderProvider = defaultEncoderProvider;
      return this;
    }

    public RpcPayloadEncoders build() {
      checkNotNull(defaultEncoderProvider, "Must provide a default encoder");
      return new RpcPayloadEncoders(Collections.unmodifiableMap(encoders), defaultEncoderProvider);
    }
  }
}
