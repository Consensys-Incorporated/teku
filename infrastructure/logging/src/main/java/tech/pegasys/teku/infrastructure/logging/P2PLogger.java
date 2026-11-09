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

package tech.pegasys.teku.infrastructure.logging;

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class P2PLogger {
  public static final P2PLogger P2P_LOG = new P2PLogger(LoggingConfigurator.P2P_LOGGER_NAME);

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger log;

  private final boolean isIncludeP2pWarnings = LoggingConfigurator.isIncludeP2pWarnings();

  public P2PLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void onGossipMessageDecodingError(
      final String topic, final Bytes originalMessage, final Throwable error) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Failed to decode gossip message on topic {}, raw message: {}",
          topic,
          originalMessage,
          error);
    }
  }

  public void onGossipRejected(
      final String topic,
      final Supplier<Bytes> decodedMessage,
      final Optional<String> description) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Rejecting gossip message on topic {}, reason: {}, decoded message: {}",
          topic,
          description.orElse("failed validation"),
          decodedMessage.get());
    }
  }

  public void onInvalidBlock(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final Bytes blockSsz,
      final String failureReason,
      final Optional<Throwable> failureCause) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Rejecting invalid block at slot {} with root {} because {}. Full block data: {}",
          slot,
          blockRoot,
          failureReason,
          blockSsz,
          failureCause.orElse(null));
    }
  }

  public void onInvalidExecutionPayload(
      final UInt64 slot,
      final UInt64 builderIndex,
      final Bytes32 blockRoot,
      final Bytes executionPayloadSsz,
      final String failureReason,
      final Optional<Throwable> failureCause) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Rejecting invalid execution payload at slot {} with builder {} and block root {} because {}. Full execution payload data: {}",
          slot,
          builderIndex,
          blockRoot,
          failureReason,
          executionPayloadSsz,
          failureCause.orElse(null));
    }
  }
}
