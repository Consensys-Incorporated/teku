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

package tech.pegasys.teku.validator.client.duties.execution;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.client.Validator;

/** Used to notify subscribers for events related to bids included in blocks */
public interface ExecutionPayloadBidEventsChannel extends VoidReturningChannelInterface {

  /** Block proposed by {@code validator} is using a self-built bid */
  void onSelfBuiltBidIncludedInBlock(
      Validator validator, ForkInfo forkInfo, ExecutionPayloadBid bid, Bytes32 beaconBlockRoot);
}
