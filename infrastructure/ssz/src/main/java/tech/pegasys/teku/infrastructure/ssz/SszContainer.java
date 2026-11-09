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

package tech.pegasys.teku.infrastructure.ssz;

import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;

/**
 * Base class for immutable containers. Since containers are heterogeneous their generic child type
 * is {@link SszData}
 */
public interface SszContainer extends SszComposite<SszData> {

  @Override
  SszContainerSchema<?> getSchema();

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  // container is heterogeneous by its nature so making unsafe cast here
  // is more convenient and is not less safe
  default <C extends SszData> C getAny(final int index) {
    return (C) get(index);
  }
}
