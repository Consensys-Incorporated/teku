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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class BeaconChainMethodIdsTest {
  private final Spec spec = TestSpecFactory.createDefault();
  protected final RpcEncoding rpcEncoding =
      RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());

  @Test
  public void getProtocolId() {

    final String protocolId =
        BeaconChainMethodIds.getMethodId(
            BeaconChainMethodIds.BEACON_BLOCKS_BY_RANGE, 2, rpcEncoding);
    final String expected = "/eth2/beacon_chain/req/beacon_blocks_by_range/2/ssz_snappy";
    assertThat(protocolId).isEqualTo(expected);
  }

  @Test
  public void extractVersion() {
    String protocolId =
        BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 1, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(1);

    protocolId = BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 2, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(2);

    protocolId = BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 10, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(10);

    protocolId = BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 11, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(11);
  }

  @ParameterizedTest
  @MethodSource("rpcMethods")
  public void testExtractVersionsFromMethodId(final String method) {
    IntStream.range(1, 10)
        .forEach(
            expectedVersion -> {
              final String methodId =
                  BeaconChainMethodIds.getMethodId(method, expectedVersion, rpcEncoding);
              assertThat(BeaconChainMethodIds.extractVersion(methodId, method))
                  .isEqualTo(expectedVersion);
            });
  }

  private static Stream<Arguments> rpcMethods() {
    return Stream.of(
        Arguments.of(BeaconChainMethodIds.STATUS),
        Arguments.of(BeaconChainMethodIds.GOODBYE),
        Arguments.of(BeaconChainMethodIds.BEACON_BLOCKS_BY_ROOT),
        Arguments.of(BeaconChainMethodIds.BEACON_BLOCKS_BY_RANGE),
        Arguments.of(BeaconChainMethodIds.BLOB_SIDECARS_BY_ROOT),
        Arguments.of(BeaconChainMethodIds.BLOB_SIDECARS_BY_RANGE),
        Arguments.of(BeaconChainMethodIds.DATA_COLUMN_SIDECARS_BY_ROOT),
        Arguments.of(BeaconChainMethodIds.DATA_COLUMN_SIDECARS_BY_RANGE),
        Arguments.of(BeaconChainMethodIds.EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT),
        Arguments.of(BeaconChainMethodIds.EXECUTION_PAYLOAD_ENVELOPES_BY_RANGE),
        Arguments.of(BeaconChainMethodIds.GET_METADATA),
        Arguments.of(BeaconChainMethodIds.PING));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rpcMethodsWithExtractVersionFunction")
  public void testSpecificExtractVersionMethods(
      final String method, final Function<String, Integer> versionExtractor) {
    final int expectedVersion = 99;
    final String methodId = BeaconChainMethodIds.getMethodId(method, expectedVersion, rpcEncoding);
    assertThat(versionExtractor.apply(methodId)).isEqualTo(expectedVersion);
  }

  private static Stream<Arguments> rpcMethodsWithExtractVersionFunction() {
    return Stream.of(
        Arguments.of(
            BeaconChainMethodIds.STATUS,
            (Function<String, Integer>) BeaconChainMethodIds::extractStatusVersion),
        Arguments.of(
            BeaconChainMethodIds.BEACON_BLOCKS_BY_ROOT,
            (Function<String, Integer>) BeaconChainMethodIds::extractBeaconBlocksByRootVersion),
        Arguments.of(
            BeaconChainMethodIds.BEACON_BLOCKS_BY_RANGE,
            (Function<String, Integer>) BeaconChainMethodIds::extractBeaconBlocksByRangeVersion),
        Arguments.of(
            BeaconChainMethodIds.GET_METADATA,
            (Function<String, Integer>) BeaconChainMethodIds::extractGetMetadataVersion));
  }
}
