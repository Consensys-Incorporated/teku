/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.execution;

import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

public interface ReceivedExecutionPayloadEventsChannel extends VoidReturningChannelInterface {

  /** Execution payload passes validation rules of the `execution_payload` topic */
  void onExecutionPayloadValidated(SignedExecutionPayloadEnvelope executionPayload);

  /** Execution payload passes the data availability checks * */
  void onExecutionPayloadAvailable(SignedExecutionPayloadEnvelope executionPayload);

  /** Successfully imported on the fork-choice `on_execution_payload` handler */
  void onExecutionPayloadImported(
      SignedExecutionPayloadEnvelope executionPayload, boolean executionOptimistic);
}
