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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ProtobufEncoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessageSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SszSnappyGossipEncodingTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final GossipEncoding encoding = GossipEncoding.SSZ_SNAPPY;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconStateSchema<?, ?> beaconStateSchema =
      spec.getGenesisSchemaDefinitions().getBeaconStateSchema();
  private final SignedBeaconBlockSchema signedBeaconBlockSchema =
      spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema();
  private final String topicName =
      GossipTopics.getTopic(Bytes4.fromHexStringLenient("0x01"), "testing", encoding);

  private <T extends SszData> T decode(
      final String topic,
      final GossipEncoding encoding,
      final Bytes data,
      final SszSchema<T> valueType)
      throws DecodingException {
    return encoding.decodeMessage(
        encoding
            .createPreparedGossipMessageFactory(__ -> Optional.of(SpecMilestone.PHASE0))
            .create(topic, data, valueType, spec.getNetworkingConfig(), Optional.empty()),
        valueType);
  }

  @Test
  public void decode_rejectMessageWithHugeUncompressedLengthPriorToDecompression() {
    Bytes hugeLength = ProtobufEncoder.encodeVarInt(Integer.MAX_VALUE);
    final Bytes data =
        Bytes.concatenate(hugeLength, Bytes.fromHexString("000000000000000000000000"));

    assertThatThrownBy(() -> decode(topicName, encoding, data, signedBeaconBlockSchema))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void roundTrip_aggregate() throws DecodingException {
    final SignedAggregateAndProof original = dataStructureUtil.randomSignedAggregateAndProof();

    final Bytes encoded = encoding.encode(original);
    final SignedAggregateAndProof decoded =
        decode(topicName, encoding, encoded, original.getSchema());

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  public void roundTrip_attestation() throws DecodingException {
    final Attestation original = dataStructureUtil.randomAttestation();

    final Bytes encoded = encoding.encode(original);
    final Attestation decoded = decode(topicName, encoding, encoded, original.getSchema());

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  public void roundTrip_block() throws DecodingException {
    final SignedBeaconBlock original = dataStructureUtil.randomSignedBeaconBlock(1);

    final Bytes encoded = encoding.encode(original);
    final SignedBeaconBlock decoded = decode(topicName, encoding, encoded, signedBeaconBlockSchema);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  public void decode_emptyValue() {
    assertThatThrownBy(() -> decode(topicName, encoding, Bytes.EMPTY, beaconStateSchema))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_invalidData() {
    final Bytes data = Bytes.fromHexString("0xB1AB1A");
    assertThatThrownBy(() -> decode(topicName, encoding, data, beaconStateSchema))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_toWrongType() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = encoding.encode(block);
    assertThatThrownBy(() -> decode(topicName, encoding, encoded, beaconStateSchema))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_extraDataAppended() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = Bytes.concatenate(encoding.encode(block), Bytes.fromHexString("0x01"));
    assertThatThrownBy(() -> decode(topicName, encoding, encoded, beaconStateSchema))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_rejectMessageShorterThanValidLength() {
    assertThatThrownBy(
            () -> decode(topicName, encoding, Bytes.of(1, 2, 3), signedBeaconBlockSchema))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_rejectMessageLongerThanValidLength() {
    assertThatThrownBy(
            () ->
                decode(
                    topicName,
                    encoding,
                    Bytes.wrap(new byte[512]),
                    new StatusMessageSchemaPhase0()))
        .isInstanceOf(DecodingException.class);
  }
}
