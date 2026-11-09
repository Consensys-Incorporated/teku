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

package tech.pegasys.teku.networking.p2p.gossip;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface TopicHandler {
  /**
   * Preprocess 'raw' Gossip message returning the instance which may calculate Gossip 'message-id'
   * and cache intermediate data for later message handling with {@link
   * #handleMessage(PreparedGossipMessage)}. Also packs it with arrivalTimestamp when available
   */
  PreparedGossipMessage prepareMessage(Bytes payload, Optional<UInt64> arrivalTimestamp);

  /**
   * Validates and handles gossip message preprocessed earlier by {@link #prepareMessage(Bytes,
   * Optional)}
   *
   * @param message The preprocessed gossip message
   * @return Message validation promise
   */
  SafeFuture<ValidationResult> handleMessage(PreparedGossipMessage message);

  /**
   * Expected maximum message size
   *
   * @return max message size in bytes
   */
  int getMaxMessageSize();
}
