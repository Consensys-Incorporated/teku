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

package tech.pegasys.teku.ethereum.executionclient.methods;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonRpcRequestParams {

  private final List<Object> params;

  private JsonRpcRequestParams(final List<Object> params) {
    this.params = params;
  }

  public <T> T getRequiredParameter(final int index, final Class<T> paramClass) {
    return getOptionalParameter(index, paramClass)
        .orElseThrow(
            () -> new IllegalArgumentException("Missing required parameter at index " + index));
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> getOptionalParameter(final int index, final Class<T> paramClass) {
    if (params == null || params.isEmpty() || params.size() <= index || params.get(index) == null) {
      return Optional.empty();
    }

    final Object param = params.get(index);

    if (!paramClass.isAssignableFrom(param.getClass())) {
      throw new IllegalArgumentException(
          "Invalid type " + paramClass.getSimpleName() + " for parameter at index " + index);
    }

    return Optional.of((T) param);
  }

  @SuppressWarnings({"unchecked", "unused"})
  public <T> List<T> getRequiredListParameter(final int index, final Class<T> __) {
    return getRequiredParameter(index, (Class<List<T>>) (Object) List.class);
  }

  public static class Builder {

    private final List<Object> params = new ArrayList<>();

    public Builder add(final Object param) {
      this.params.add(param);
      return this;
    }

    public Builder addOptional(final Optional<?> param) {
      param.ifPresent(this::add);
      return this;
    }

    public JsonRpcRequestParams build() {
      return new JsonRpcRequestParams(params);
    }
  }
}
