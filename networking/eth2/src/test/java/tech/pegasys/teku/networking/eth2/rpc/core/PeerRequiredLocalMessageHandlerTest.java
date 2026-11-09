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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.netty.channel.socket.ChannelOutputShutdownException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;

class PeerRequiredLocalMessageHandlerTest {

  @SuppressWarnings("unchecked")
  private final ResponseCallback<SszData> callback = mock(ResponseCallback.class);

  private final PeerRequiredLocalMessageHandler<Object, SszData> handler =
      new PeerRequiredLocalMessageHandler<>() {
        @Override
        protected void onIncomingMessage(
            final String protocolId,
            final Eth2Peer peer,
            final Object message,
            final ResponseCallback<SszData> callback) {}
      };

  @Test
  void shouldHandleStopSendingAsExpectedStreamClose() {
    final Throwable error =
        new RuntimeException(new ChannelOutputShutdownException("STOP_SENDING frame received"));

    try (final LogCaptor logCaptor =
        LogCaptor.forClass(PeerRequiredLocalMessageHandler.class, Level.TRACE)) {
      handler.handleError(error, callback, "thingy");

      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getTraceLogs())
          .anySatisfy(log -> assertThat(log).contains("Stream closed while sending requested"));
    }
    verify(callback).completeWithUnexpectedError(error);
  }

  @Test
  void shouldHandleClosedChannelAsExpectedStreamClose() {
    final Throwable error = new RuntimeException(new ClosedChannelException());

    try (final LogCaptor logCaptor =
        LogCaptor.forClass(PeerRequiredLocalMessageHandler.class, Level.TRACE)) {
      handler.handleError(error, callback, "thingy");

      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
    verify(callback).completeWithUnexpectedError(error);
  }

  @Test
  void shouldHandleStreamTimeoutAsExpectedStreamClose() {
    final Throwable error = new RpcTimeoutException("Timed out writing response", Duration.ZERO);

    try (final LogCaptor logCaptor =
        LogCaptor.forClass(PeerRequiredLocalMessageHandler.class, Level.TRACE)) {
      handler.handleError(error, callback, "thingy");

      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getTraceLogs())
          .anySatisfy(log -> assertThat(log).contains("Stream closed while sending requested"));
    }
    verify(callback).completeWithUnexpectedError(error);
  }

  @Test
  void shouldLogUnexpectedErrorAtErrorLevel() {
    final Throwable error = new RuntimeException(new IllegalStateException("Boom"));

    try (final LogCaptor logCaptor = LogCaptor.forClass(PeerRequiredLocalMessageHandler.class)) {
      handler.handleError(error, callback, "thingy");

      logCaptor.assertErrorLog("Failed to process thingy request");
    }
    verify(callback).completeWithUnexpectedError(error);
  }
}
