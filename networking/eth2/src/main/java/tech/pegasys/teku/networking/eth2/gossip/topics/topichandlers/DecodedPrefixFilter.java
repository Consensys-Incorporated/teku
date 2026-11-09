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

package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage.DecodedMessagePrefix;

/**
 * Decides from the leading bytes of a gossip message's uncompressed payload whether it is worth
 * queueing for decoding and validation. Runs on the gossipsub thread, so it must be cheap.
 */
@FunctionalInterface
public interface DecodedPrefixFilter {
  DecodedPrefixFilter ACCEPT_ALL = decodedPrefix -> true;

  /**
   * @param decodedPrefix up to {@link DecodedMessagePrefix#MAX_LENGTH} leading bytes of the
   *     uncompressed payload
   * @return false to ignore the message without decoding it
   */
  boolean shouldProcess(Bytes decodedPrefix);
}
