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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding.ForkDigestToMilestone;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;

/**
 * {@link PreparedGossipMessage} implementation which calculates Gossip 'message-id' according to
 * Eth2 spec based on uncompressed gossip message payload: <code>
 *   SHA256(MESSAGE_DOMAIN_VALID_SNAPPY + snappy_decompress(message.data))[:20]
 * </code> The message payload is uncompressed lazily and cached for the final message handling:
 * {@link tech.pegasys.teku.networking.p2p.gossip.TopicHandler#handleMessage(PreparedGossipMessage)}
 */
class SnappyPreparedGossipMessage implements PreparedGossipMessage {
  private final Bytes compressedData;
  private final SszSchema<?> valueType;
  private final Uncompressor snappyCompressor;
  private final MessageIdCalculator messageIdCalculator;
  private final NetworkingSpecConfig networkingConfig;
  private final Optional<UInt64> arrivalTimestamp;

  private final Supplier<DecodedMessageResult> decodedResult =
      Suppliers.memoize(this::decodeMessage);

  static SnappyPreparedGossipMessage createUnknown(
      final String topic,
      final Bytes compressedData,
      final ForkDigestToMilestone forkDigestToMilestone,
      final NetworkingSpecConfig networkingConfig,
      final Optional<UInt64> arrivalTimestamp) {
    return new SnappyPreparedGossipMessage(
        topic,
        compressedData,
        forkDigestToMilestone,
        null,
        null,
        networkingConfig,
        arrivalTimestamp);
  }

  static SnappyPreparedGossipMessage create(
      final String topic,
      final Bytes compressedData,
      final ForkDigestToMilestone forkDigestToMilestone,
      final SszSchema<?> valueType,
      final Uncompressor snappyCompressor,
      final NetworkingSpecConfig networkingConfig,
      final Optional<UInt64> arrivalTimestamp) {
    return new SnappyPreparedGossipMessage(
        topic,
        compressedData,
        forkDigestToMilestone,
        valueType,
        snappyCompressor,
        networkingConfig,
        arrivalTimestamp);
  }

  private SnappyPreparedGossipMessage(
      final String topic,
      final Bytes compressedData,
      final ForkDigestToMilestone forkDigestToMilestone,
      final SszSchema<?> valueType,
      final Uncompressor snappyCompressor,
      final NetworkingSpecConfig networkingConfig,
      final Optional<UInt64> arrivalTimestamp) {
    this.compressedData = compressedData;
    this.valueType = valueType;
    this.snappyCompressor = snappyCompressor;
    this.networkingConfig = networkingConfig;
    this.messageIdCalculator =
        createMessageIdCalculator(topic, compressedData, forkDigestToMilestone);
    this.arrivalTimestamp = arrivalTimestamp;
  }

  private MessageIdCalculator createMessageIdCalculator(
      final String topic,
      final Bytes compressedData,
      final ForkDigestToMilestone forkDigestToMilestone) {
    final Bytes4 forkDigest = GossipTopics.extractForkDigest(topic);
    final SpecMilestone milestone =
        forkDigestToMilestone
            .getMilestone(forkDigest)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to associate a milestone with the forkDigest in topic: " + topic));

    switch (milestone) {
      case PHASE0:
        return new MessageIdCalculatorPhase0(compressedData, networkingConfig);
      case ALTAIR:
      default:
        return new MessageIdCalculatorAltair(compressedData, topic, networkingConfig);
    }
  }

  @Override
  public DecodedMessageResult getDecodedMessage() {
    return decodedResult.get();
  }

  private DecodedMessageResult decodeMessage() {
    try {
      if (valueType == null) {
        return DecodedMessageResult.failed();
      } else {
        final Bytes decodedMessage = uncompressPayload(networkingConfig.getMaxPayloadSize());
        return DecodedMessageResult.successful(decodedMessage);
      }
    } catch (DecodingException e) {
      return DecodedMessageResult.failed(e);
    }
  }

  @Override
  public Bytes getOriginalMessage() {
    return compressedData;
  }

  private Optional<Bytes> getUncompressed() {
    return getDecodedMessage().getDecodedMessage();
  }

  private Bytes uncompressPayload(final long maxUncompressedLength) throws DecodingException {
    return snappyCompressor.uncompress(
        compressedData, valueType.getNetworkSszLengthBounds(), maxUncompressedLength);
  }

  @Override
  public Bytes getMessageId() {
    return getUncompressed()
        .map(messageIdCalculator::getValidMessageId)
        .orElseGet(messageIdCalculator::getInvalidMessageId);
  }

  @Override
  public Optional<UInt64> getArrivalTimestamp() {
    return arrivalTimestamp;
  }

  @FunctionalInterface
  interface Uncompressor {

    Bytes uncompress(
        final Bytes compressedData,
        final SszLengthBounds lengthBounds,
        final long maxUncompressedLengthInBytes)
        throws DecodingException;
  }
}
