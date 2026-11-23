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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.statetransition.pq.timing.PqTiming;

/** Describes a decoded PQ gossip message for the PQ timing export. */
@FunctionalInterface
public interface PqGossipTiming<MessageT> {

  void received(PqTiming.Receipt receipt, MessageT message, Bytes messageId, int messageBytes);

  /** Single attestations on an attestation subnet. */
  static PqGossipTiming<Attestation> individual(final int subnetId) {
    return (receipt, attestation, messageId, messageBytes) ->
        receipt.received(
            "individual",
            messageId,
            attestation.getData().getSlot(),
            Optional.of(attestation.getFirstCommitteeIndex()),
            Optional.of(subnetId),
            1,
            messageBytes,
            attestation.getData().hashTreeRoot());
  }

  /** Committee aggregates on the aggregate topic. */
  static PqGossipTiming<SignedAggregateAndProofPq> aggregate() {
    return (receipt, signedAggregate, messageId, messageBytes) -> {
      final AttestationPq aggregate = signedAggregate.getMessage().getAggregate();
      receipt.received(
          "aggregate",
          messageId,
          aggregate.getData().getSlot(),
          Optional.of(aggregate.getFirstCommitteeIndex()),
          Optional.empty(),
          aggregate.getAggregationBits().getBitCount(),
          messageBytes,
          aggregate.getData().hashTreeRoot());
    };
  }
}
