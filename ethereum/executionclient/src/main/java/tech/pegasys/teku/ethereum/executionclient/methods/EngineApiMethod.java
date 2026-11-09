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

public enum EngineApiMethod {
  ENGINE_NEW_PAYLOAD("engine_newPayload"),
  ENGINE_GET_PAYLOAD("engine_getPayload"),
  ENGINE_FORK_CHOICE_UPDATED("engine_forkchoiceUpdated"),
  ENGINE_GET_PAYLOAD_BODIES_BY_HASH("engine_getPayloadBodiesByHash");

  private final String name;

  EngineApiMethod(final String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
