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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;

class MessageIdCalculatorAltair extends MessageIdCalculator {
  private final Bytes rawMessageData;
  private final String topic;
  private final NetworkingSpecConfig networkingConfig;

  public MessageIdCalculatorAltair(
      final Bytes rawMessageData, final String topic, final NetworkingSpecConfig networkingConfig) {
    this.rawMessageData = rawMessageData;
    this.topic = topic;
    this.networkingConfig = networkingConfig;
  }

  @Override
  protected Bytes validMessageIdData(final Bytes uncompressedData) {
    final Bytes topicBytes = getTopicBytes();
    final Bytes topicBytesLength = encodeTopicLength(topicBytes);
    return Bytes.wrap(
        networkingConfig.getMessageDomainValidSnappy().getWrappedBytes(),
        topicBytesLength,
        topicBytes,
        uncompressedData);
  }

  @Override
  protected Bytes invalidMessageIdData() {
    final Bytes topicBytes = getTopicBytes();
    final Bytes topicBytesLength = encodeTopicLength(topicBytes);
    return Bytes.wrap(
        networkingConfig.getMessageDomainInvalidSnappy().getWrappedBytes(),
        topicBytesLength,
        topicBytes,
        rawMessageData);
  }

  private Bytes getTopicBytes() {
    return Bytes.of(topic.getBytes(StandardCharsets.UTF_8));
  }

  private Bytes encodeTopicLength(final Bytes topicBytes) {
    return MathHelpers.uint64ToBytes(topicBytes.size());
  }
}
