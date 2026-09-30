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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GossipTopicShapeTest {

  @ParameterizedTest
  @CsvSource({
    "/eth2/aabbccdd/beacon_block/ssz_snappy, beacon_block",
    "/eth2/aabbccdd/beacon_aggregate_and_proof/ssz_snappy, beacon_aggregate_and_proof",
    "/eth2/aabbccdd/voluntary_exit/ssz_snappy, voluntary_exit",
    "/eth2/aabbccdd/beacon_attestation_0/ssz_snappy, beacon_attestation",
    "/eth2/aabbccdd/beacon_attestation_63/ssz_snappy, beacon_attestation",
    "/eth2/aabbccdd/sync_committee_3/ssz_snappy, sync_committee",
    "/eth2/aabbccdd/blob_sidecar_5/ssz_snappy, blob_sidecar",
    "/eth2/aabbccdd/data_column_sidecar_127/ssz_snappy, data_column_sidecar",
  })
  void stripsForkDigestAndSubnetIndex(final String topic, final String expectedShape) {
    assertThat(GossipTopicShape.of(topic)).isEqualTo(expectedShape);
  }

  @Test
  void collapsesEverySubnetOfATopicOntoOneLabel() {
    assertThat(
            IntStream.range(0, 128)
                .mapToObj(i -> "/eth2/aabbccdd/data_column_sidecar_" + i + "/ssz_snappy")
                .map(GossipTopicShape::of)
                .distinct())
        .containsExactly("data_column_sidecar");
  }

  @Test
  void collapsesEveryForkDigestOntoOneLabel() {
    assertThat(GossipTopicShape.of("/eth2/11111111/beacon_block/ssz_snappy"))
        .isEqualTo(GossipTopicShape.of("/eth2/22222222/beacon_block/ssz_snappy"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not-a-topic",
        "/eth2/aabbccdd/beacon_block",
        "/eth2//beacon_block/ssz_snappy",
        "/eth1/aabbccdd/beacon_block/ssz_snappy",
        "/eth2/aabbccdd/nested/name/ssz_snappy",
      })
  void bucketsUnparseableTopicsRatherThanMintingASeries(final String topic) {
    assertThat(GossipTopicShape.of(topic)).isEqualTo(GossipTopicShape.OTHER);
  }

  @Test
  void bucketsNullTopic() {
    assertThat(GossipTopicShape.of(null)).isEqualTo(GossipTopicShape.OTHER);
  }
}
