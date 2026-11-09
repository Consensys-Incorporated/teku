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

package tech.pegasys.teku.networking.p2p.peer;

import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;

public enum DisconnectReason {
  IRRELEVANT_NETWORK(GoodbyeMessage.REASON_IRRELEVANT_NETWORK, true),
  UNABLE_TO_VERIFY_NETWORK(GoodbyeMessage.REASON_UNABLE_TO_VERIFY_NETWORK, true),
  TOO_MANY_PEERS(GoodbyeMessage.REASON_TOO_MANY_PEERS, false),
  REMOTE_FAULT(GoodbyeMessage.REASON_FAULT_ERROR, false),
  UNRESPONSIVE(GoodbyeMessage.REASON_FAULT_ERROR, false),
  SHUTTING_DOWN(GoodbyeMessage.REASON_CLIENT_SHUT_DOWN, false),
  RATE_LIMITING(GoodbyeMessage.REASON_RATE_LIMITING, false),
  BAD_SCORE(GoodbyeMessage.REASON_BAD_SCORE, false),
  BANNED(GoodbyeMessage.REASON_BANNED, false),
  NO_STATUS_RECEIVED(GoodbyeMessage.REASON_FAULT_ERROR, false),
  DUPLICATE_CONNECTION(GoodbyeMessage.REASON_FAULT_ERROR, false);

  private final UInt64 reasonCode;
  private final boolean isPermanent;

  DisconnectReason(final UInt64 reasonCode, final boolean isPermanent) {
    this.reasonCode = reasonCode;
    this.isPermanent = isPermanent;
  }

  public static Optional<DisconnectReason> fromReasonCode(final UInt64 reasonCode) {
    return Stream.of(values())
        .filter(reason -> reason.getReasonCode().equals(reasonCode))
        .findAny();
  }

  public UInt64 getReasonCode() {
    return reasonCode;
  }

  public boolean isPermanent() {
    return isPermanent;
  }
}
