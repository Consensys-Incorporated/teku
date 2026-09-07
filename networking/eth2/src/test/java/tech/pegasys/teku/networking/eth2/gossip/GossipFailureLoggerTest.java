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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.networking.eth2.gossip.GossipFailureLogger.createNonSuppressing;
import static tech.pegasys.teku.networking.eth2.gossip.GossipFailureLogger.createSuppressing;

import io.libp2p.core.SemiDuplexNoOutboundStreamException;
import io.libp2p.pubsub.DroppedRpcPartsException;
import io.libp2p.pubsub.MessageAlreadySeenException;
import io.libp2p.pubsub.NoPeersForOutboundMessageException;
import io.netty.channel.socket.ChannelOutputShutdownException;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class GossipFailureLoggerTest {

  public static final String ALREADY_SEEN_MESSAGE =
      "Failed to publish thingy(s) for slot 1 because the message has already been seen";
  public static final Optional<UInt64> SLOT = Optional.of(UInt64.ONE);
  public static final SemiDuplexNoOutboundStreamException NO_ACTIVE_STREAM_EXCEPTION =
      new SemiDuplexNoOutboundStreamException("So Lonely");
  public static final NoPeersForOutboundMessageException NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION =
      new NoPeersForOutboundMessageException("no peers");
  public static final DroppedRpcPartsException SLOW_PEER_EXCEPTION =
      new DroppedRpcPartsException("Queued low priority RPC parts were dropped");

  private final GossipFailureLogger loggerSuppressing = createSuppressing("thingy");
  private final GossipFailureLogger loggerNoSuppression = createNonSuppressing("thingy");

  @Test
  void shouldLogAlreadySeenErrorsAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", new MessageAlreadySeenException("Dupe")), SLOT);
      logCaptor.assertDebugLog(ALREADY_SEEN_MESSAGE);
    }
  }

  @Test
  void shouldLogFirstNoPeersErrorsAtWarningLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(noPeersMessage(SLOT, true));
    }
  }

  @Test
  void shouldLogFirstNoActiveStreamErrorsAtWarningLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(new RuntimeException("Foo", NO_ACTIVE_STREAM_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(noActiveStreamMessage(SLOT, true));
    }
  }

  @Test
  void shouldLogRepeatedNoPeersErrorsAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.clearLogs();

      loggerSuppressing.log(
          new IllegalStateException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.assertDebugLog(noPeersMessage(SLOT, true));
    }
  }

  @Test
  void shouldLogRepeatedNoPeersErrorsWhenNoSuppression() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerNoSuppression.log(
          new RuntimeException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.clearLogs();

      loggerNoSuppression.log(
          new IllegalStateException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(noPeersMessage(SLOT, false));
    }
  }

  @Test
  void shouldLogNoPeersErrorsWithDifferentSlotsAtWarnLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(noPeersMessage(SLOT, true));

      loggerSuppressing.log(
          new IllegalStateException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION),
          Optional.of(UInt64.valueOf(2)));
      logCaptor.assertWarnLog(noPeersMessage(Optional.of(UInt64.valueOf(2)), true));
    }
  }

  @Test
  void shouldLogNoPeersErrorsAtWarnLevelWhenSeparatedByADifferentException() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(noPeersMessage(SLOT, true));
      logCaptor.clearLogs();

      loggerSuppressing.log(new MessageAlreadySeenException("Dupe"), SLOT);

      loggerSuppressing.log(
          new IllegalStateException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(noPeersMessage(SLOT, true));
    }
  }

  @Test
  void shouldLogClosedChannelExceptionAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(new RuntimeException("Foo", new ClosedChannelException()), SLOT);
      logCaptor.assertDebugLog(streamClosedMessage(SLOT, true));
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogChannelOutputShutdownExceptionAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException(
              "Foo", new ChannelOutputShutdownException("STOP_SENDING frame received")),
          SLOT);
      logCaptor.assertDebugLog(streamClosedMessage(SLOT, true));
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogFirstSlowPeerErrorsAtWarningLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(new RuntimeException("Foo", SLOW_PEER_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(slowPeerMessage(SLOT, true));
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogRepeatedSlowPeerErrorsAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(new RuntimeException("Foo", SLOW_PEER_EXCEPTION), SLOT);
      logCaptor.clearLogs();

      loggerSuppressing.log(new IllegalStateException("Foo", SLOW_PEER_EXCEPTION), SLOT);
      logCaptor.assertDebugLog(slowPeerMessage(SLOT, true));
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogSlowPeerErrorsWithoutSuppression() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerNoSuppression.log(new RuntimeException("Foo", SLOW_PEER_EXCEPTION), SLOT);
      logCaptor.clearLogs();

      loggerNoSuppression.log(new IllegalStateException("Foo", SLOW_PEER_EXCEPTION), SLOT);
      logCaptor.assertWarnLog(slowPeerMessage(SLOT, false));
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogFirstGenericErrorAtErrorLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(new RuntimeException("Foo", new IllegalStateException("Boom")), SLOT);
      logCaptor.assertErrorLog(genericError(SLOT, true));
    }
  }

  @Test
  void shouldLogRepeatedGenericErrorsAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(new RuntimeException("Foo", new IllegalStateException("Boom")), SLOT);
      logCaptor.clearLogs();

      loggerSuppressing.log(
          new IllegalStateException("Foo", new IllegalStateException("goes the dynamite")), SLOT);
      logCaptor.assertDebugLog(genericError(SLOT, true));
    }
  }

  @Test
  void shouldLogMultipleGenericErrorsWithDifferentCausesAtErrorLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(new RuntimeException("Foo", new IllegalStateException("Boom")), SLOT);
      logCaptor.assertErrorLog(genericError(SLOT, true));
      logCaptor.clearLogs();

      loggerSuppressing.log(
          new IllegalStateException("Foo", new IllegalArgumentException("goes the dynamite")),
          SLOT);
      logCaptor.assertErrorLog(genericError(SLOT, true));
    }
  }

  @Test
  void shouldLogGenericErrorsWithoutSuppression() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", new IllegalStateException("Boom")), Optional.empty());
      logCaptor.clearLogs();

      loggerSuppressing.log(
          new IllegalStateException("Foo", new IllegalStateException("goes the dynamite")),
          Optional.empty());
      logCaptor.assertErrorLog(genericError(Optional.empty(), true));
    }
  }

  @Test
  void shouldLogNoPeersErrorsAtWarnLevelWithoutSuppression() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION), Optional.empty());
      logCaptor.clearLogs();

      loggerSuppressing.log(
          new IllegalStateException("Foo", NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION),
          Optional.empty());
      logCaptor.assertWarnLog(noPeersMessage(Optional.empty(), true));
    }
  }

  @Test
  void shouldLogNoActiveStreamErrorsWithoutSuppression() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      loggerSuppressing.log(
          new RuntimeException("Foo", NO_ACTIVE_STREAM_EXCEPTION), Optional.empty());
      logCaptor.clearLogs();

      loggerSuppressing.log(
          new IllegalStateException("Foo", NO_ACTIVE_STREAM_EXCEPTION), Optional.empty());
      logCaptor.assertWarnLog(noActiveStreamMessage(Optional.empty(), true));
    }
  }

  private static String noPeersMessage(final Optional<UInt64> slot, final boolean shouldSuppress) {
    return "Failed to publish thingy"
        + (shouldSuppress ? "(s)" : "")
        + slot.map(s -> " for slot " + s).orElse("")
        + "; "
        + NO_PEERS_FOR_OUTBOUND_MESSAGE_EXCEPTION.getMessage();
  }

  private static String noActiveStreamMessage(
      final Optional<UInt64> slot, final boolean shouldSuppress) {
    return "Failed to publish thingy"
        + (shouldSuppress ? "(s)" : "")
        + slot.map(s -> " for slot " + s).orElse("")
        + " because no active outbound stream for the required gossip topic";
  }

  private static String slowPeerMessage(final Optional<UInt64> slot, final boolean shouldSuppress) {
    return "Failed to publish thingy"
        + (shouldSuppress ? "(s)" : "")
        + slot.map(s -> " for slot " + s).orElse("")
        + " because a peer's outbound gossip queue was full and low-priority messages were dropped";
  }

  private static String genericError(final Optional<UInt64> slot, final boolean shouldSuppress) {
    return "Failed to publish thingy"
        + (shouldSuppress ? "(s)" : "")
        + slot.map(s -> " for slot " + s).orElse("");
  }

  private static String streamClosedMessage(
      final Optional<UInt64> slot, final boolean shouldSuppress) {
    return "Failed to publish thingy"
        + (shouldSuppress ? "(s)" : "")
        + slot.map(s -> " for slot " + s).orElse("")
        + " because the stream was closed";
  }
}
