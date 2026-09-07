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

package tech.pegasys.teku.networking.eth2.gossip;

import com.google.common.base.Throwables;
import io.libp2p.core.SemiDuplexNoOutboundStreamException;
import io.libp2p.pubsub.DroppedRpcPartsException;
import io.libp2p.pubsub.MessageAlreadySeenException;
import io.libp2p.pubsub.NoPeersForOutboundMessageException;
import io.netty.channel.socket.ChannelOutputShutdownException;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GossipFailureLogger {
  private static final Logger LOG = LogManager.getLogger();

  private final boolean shouldSuppress;
  private final String messageType;
  private Optional<UInt64> lastErroredSlot;
  private Throwable lastRootCause;

  public static GossipFailureLogger createSuppressing(final String messageType) {
    return new GossipFailureLogger(messageType, true);
  }

  public static GossipFailureLogger createNonSuppressing(final String messageType) {
    return new GossipFailureLogger(messageType, false);
  }

  private GossipFailureLogger(final String messageType, final boolean shouldSuppress) {
    this.messageType = shouldSuppress ? messageType + "(s)" : messageType;
    this.shouldSuppress = shouldSuppress;
  }

  public synchronized void log(final Throwable error, final Optional<UInt64> maybeSlot) {
    final Throwable rootCause = Throwables.getRootCause(error);

    final boolean suppress;

    // can only try to suppress if we have a slot to compare
    if (shouldSuppress && maybeSlot.isPresent()) {
      suppress =
          maybeSlot.equals(lastErroredSlot)
              && rootCause.getClass().equals(lastRootCause.getClass());

    } else {
      suppress = false;
    }

    lastErroredSlot = maybeSlot;
    lastRootCause = rootCause;

    final String slotLog = maybeSlot.map(slot -> " for slot " + slot).orElse("");

    switch (lastRootCause) {
      case MessageAlreadySeenException ignored ->
          LOG.debug(
              "Failed to publish {}{} because the message has already been seen",
              messageType,
              slotLog);
      case NoPeersForOutboundMessageException ignored ->
          LOG.log(
              suppress ? Level.DEBUG : Level.WARN,
              "Failed to publish {}{}; {}",
              messageType,
              slotLog,
              lastRootCause.getMessage());
      case SemiDuplexNoOutboundStreamException ignored ->
          LOG.log(
              suppress ? Level.DEBUG : Level.WARN,
              "Failed to publish {}{} because no active outbound stream for the required gossip topic",
              messageType,
              slotLog);
      // a peer whose outbound queue is being dropped is expected during gossip mesh churn (e.g.
      // startup, or a peer that's genuinely slow); it is downscored and recovers on its own.
      case DroppedRpcPartsException ignored ->
          LOG.log(
              suppress ? Level.DEBUG : Level.WARN,
              "Failed to publish {}{} because a peer's outbound gossip queue was full and"
                  + " low-priority messages were dropped",
              messageType,
              slotLog);
      // a closed/half-closed stream is expected peer churn (e.g. a disconnecting peer, or one that
      // sent STOP_SENDING); ChannelOutputShutdownException is not a ClosedChannelException so it
      // must be handled separately.
      case ClosedChannelException ignored ->
          LOG.debug("Failed to publish {}{} because the stream was closed", messageType, slotLog);
      case ChannelOutputShutdownException ignored ->
          LOG.debug("Failed to publish {}{} because the stream was closed", messageType, slotLog);
      default ->
          LOG.log(
              suppress ? Level.DEBUG : Level.ERROR,
              "Failed to publish {}{}",
              messageType,
              slotLog,
              error);
    }
  }

  public void logWithSuppression(final Throwable error, final UInt64 slot) {
    logWithSuppression(error, slot, "");
  }

  public synchronized void logWithSuppression(
      final Throwable error, final UInt64 slot, final String details) {
    final String appendDetails = details.isEmpty() ? "" : ": " + details;
    final Throwable rootCause = Throwables.getRootCause(error);

    final boolean suppress =
        lastErroredSlot
            .filter(
                uInt64 ->
                    slot.equals(uInt64) && rootCause.getClass().equals(lastRootCause.getClass()))
            .isPresent();

    lastErroredSlot = Optional.of(slot);
    lastRootCause = rootCause;

    if (lastRootCause instanceof MessageAlreadySeenException) {
      LOG.debug(
          "Failed to publish {}(s) for slot {} because the message has already been seen {}",
          messageType,
          lastErroredSlot,
          appendDetails);
    } else if (lastRootCause instanceof NoPeersForOutboundMessageException
        || lastRootCause instanceof SemiDuplexNoOutboundStreamException) {
      LOG.log(
          suppress ? Level.DEBUG : Level.WARN,
          "Failed to publish {}(s) for slot {} because no peers were available on the required gossip topic {}",
          messageType,
          lastErroredSlot,
          appendDetails);
    } else {
      LOG.log(
          suppress ? Level.DEBUG : Level.ERROR,
          "Failed to publish {}(s) for slot {} {}",
          messageType,
          lastErroredSlot,
          appendDetails,
          error);
    }
  }
}
