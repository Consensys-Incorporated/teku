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

package tech.pegasys.teku.spec.datastructures.execution;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadHeaderBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionPayloadHeaderGloas;

public interface ExecutionPayloadHeader extends ExecutionPayloadSummary, SszContainer {

  Bytes32 getTransactionsRoot();

  boolean isHeaderOfDefaultPayload();

  default Optional<ExecutionPayloadHeaderBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadHeaderCapella> toVersionCapella() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadHeaderDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadHeaderGloas> toVersionGloas() {
    return Optional.empty();
  }
}
