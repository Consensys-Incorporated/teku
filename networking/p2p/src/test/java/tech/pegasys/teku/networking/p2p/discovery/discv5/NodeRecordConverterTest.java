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

package tech.pegasys.teku.networking.p2p.discovery.discv5;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.ATTESTATION_SUBNET_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.DAS_CUSTODY_GROUP_COUNT_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.ETH2_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.NEXT_FORK_DIGEST_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.SYNC_COMMITTEE_SUBNET_ENR_FIELD;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

// as this is a unit test, there's no need to pedantic with how we select addresses
@SuppressWarnings("AddressSelection")
class NodeRecordConverterTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalAltair();
  private static final SchemaDefinitions SCHEMA_DEFINITIONS = SPEC.getGenesisSchemaDefinitions();
  private static final Bytes PUB_KEY =
      Bytes.fromHexString("0x0295A5A50F083697FF8557F3C6FE0CDF8E8EC2141D15F19A5A45571ED9C38CE181");
  private static final Bytes IPV6_LOCALHOST =
      Bytes.fromHexString("0x00000000000000000000000000000001");
  private static final Optional<EnrForkId> ENR_FORK_ID = Optional.empty();
  private static final SszBitvectorSchema<?> ATT_SUBNET_SCHEMA =
      SCHEMA_DEFINITIONS.getAttnetsENRFieldSchema();
  private static final SszBitvector ATTNETS = ATT_SUBNET_SCHEMA.getDefault();
  private static final SszBitvectorSchema<?> SYNCNETS_SCHEMA =
      SCHEMA_DEFINITIONS.getSyncnetsENRFieldSchema();
  private static final SszBitvector SYNCNETS = SYNCNETS_SCHEMA.getDefault();
  private static final NodeRecordConverter CONVERTER = new NodeRecordConverter();
  private static final Bytes NODE_ID = CONVERTER.convertPublicKeyToNodeId(PUB_KEY);
  private static final int MAX_CUSTODY_GROUP_COUNT = 128;

  @Test
  public void shouldConvertRealEnrToDiscoveryPeer() throws Exception {
    final String enr =
        "-Iu4QMmfe-EkDnVX6k5i2LFTiDQ-q4-Cb1I01iRI-wbCD_r4Z8eujNCgZDmZXb1ZOPi1LfJaNx3Bd0QUK9wqBjwUXJQBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQO4btn3R6f6mZY_OeOxdrRenoYxCKLRReo6TnbY0JNRlIN0Y3CCIyiDdWRwgiMo";

    final NodeRecord nodeRecord = NodeRecordFactory.DEFAULT.fromBase64(enr);

    Bytes pubKey =
        Bytes.fromHexString("0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194");
    Bytes nodeId = CONVERTER.convertPublicKeyToNodeId(pubKey);
    final DiscoveryPeer expectedPeer =
        new DiscoveryPeer(
            pubKey,
            nodeId,
            Optional.of(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9000)),
            Optional.empty(),
            Optional.empty(),
            ATTNETS,
            SYNCNETS,
            Optional.empty(),
            Optional.empty());
    assertThat(CONVERTER.convertToDiscoveryPeer(nodeRecord, false, SCHEMA_DEFINITIONS))
        .contains(expectedPeer);
  }

  @Test
  public void shouldNotConvertRecordWithNoIp() {
    assertThat(convertNodeRecordWithFields(false)).isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithIpButNoPort() {
    assertThat(
            convertNodeRecordWithFields(
                false, new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1}))))
        .isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithIpAndUdpPortButNoTcpOrQuicPort() {
    assertThat(
            convertNodeRecordWithFields(
                false,
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.UDP, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithOutOfRangeTcpPort() {
    assertThat(
            convertNodeRecordWithFields(
                false,
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.TCP, 70000)))
        .isEmpty();
  }

  @Test
  public void shouldUseV4PortIfV6PortSpecifiedWithNoV6Ip() {
    assertThat(
            convertNodeRecordWithFields(
                true,
                new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
                new EnrField(EnrField.TCP, 30303)))
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 30303)),
                Optional.empty(),
                ENR_FORK_ID,
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldNotConvertRecordWithV4IpAndV6Port() {
    assertThat(
            convertNodeRecordWithFields(
                false,
                new EnrField(EnrField.IP_V4, IPV6_LOCALHOST),
                new EnrField(EnrField.TCP_V6, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithPortButNoIp() {
    assertThat(convertNodeRecordWithFields(false, new EnrField(EnrField.TCP, 30303))).isEmpty();
  }

  @Test
  public void shouldConvertIpV4Record() {
    // IP address bytes are unsigned. Make sure we handle that correctly.
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {-127, 24, 31, 22})),
            new EnrField(EnrField.TCP, 1234));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("129.24.31.22", 1234)),
                Optional.empty(),
                ENR_FORK_ID,
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldNotConvertIpV6RecordIfIpV6IsNotSupported() {
    assertThat(
            convertNodeRecordWithFields(
                false,
                new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
                new EnrField(EnrField.TCP_V6, 1234)))
        .isEmpty();
  }

  @Test
  public void shouldConvertIpV6Record() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1234));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 1234)),
                Optional.empty(),
                ENR_FORK_ID,
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldConvertDualStackRecordIfIpV6IsNotSupported() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            false,
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
            new EnrField(EnrField.TCP, 1234),
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1235));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("127.0.0.1", 1234)),
                Optional.empty(),
                ENR_FORK_ID,
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldConvertAttnets() {
    SszBitvector persistentSubnets = ATT_SUBNET_SCHEMA.ofBits(1, 8, 14, 32);
    Bytes encodedPersistentSubnets = persistentSubnets.sszSerialize();
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1234),
            new EnrField(ATTESTATION_SUBNET_ENR_FIELD, encodedPersistentSubnets));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 1234)),
                Optional.empty(),
                ENR_FORK_ID,
                persistentSubnets,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldUseEmptyAttnetsWhenFieldValueIsInvalid() {
    SszBitvector persistentSubnets = SszBitvectorSchema.create(4).ofBits(1, 2); // Incorrect length
    Bytes encodedPersistentSubnets = persistentSubnets.sszSerialize();
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1234),
            new EnrField(ATTESTATION_SUBNET_ENR_FIELD, encodedPersistentSubnets));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 1234)),
                Optional.empty(),
                ENR_FORK_ID,
                ATT_SUBNET_SCHEMA.getDefault(),
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldConvertSyncnets() {
    SszBitvector syncnets = SYNCNETS_SCHEMA.ofBits(1, 3);
    Bytes encodedSyncnets = syncnets.sszSerialize();
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1234),
            new EnrField(SYNC_COMMITTEE_SUBNET_ENR_FIELD, encodedSyncnets));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 1234)),
                Optional.empty(),
                ENR_FORK_ID,
                ATTNETS,
                syncnets,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldUseEmptySyncnetsFieldValueIsInvalid() {
    SszBitvector syncnets =
        SszBitvectorSchema.create(SYNCNETS_SCHEMA.getLength() * 2L)
            .ofBits(1, 4); // Incorrect length
    Bytes encodedSyncnets = syncnets.sszSerialize();
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1234),
            new EnrField(SYNC_COMMITTEE_SUBNET_ENR_FIELD, encodedSyncnets));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 1234)),
                Optional.empty(),
                ENR_FORK_ID,
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldConvertEnrForkId() {
    EnrForkId enrForkId = new DataStructureUtil(SPEC).randomEnrForkId();
    Bytes encodedForkId = enrForkId.sszSerialize();
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1234),
            new EnrField(ETH2_ENR_FIELD, encodedForkId));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 1234)),
                Optional.empty(),
                Optional.of(enrForkId),
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @Test
  public void shouldNotHaveEnrForkIdWhenValueIsInvalid() {
    Bytes encodedForkId = Bytes.fromHexString("0x1234");
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 1234),
            new EnrField(ETH2_ENR_FIELD, encodedForkId));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("::1", 1234)),
                Optional.empty(),
                Optional.empty(),
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.empty()));
  }

  @ParameterizedTest
  @MethodSource("getCgcFixtures")
  public void shouldDecodeCgcCorrectly(
      final String hexString, final Optional<Integer> maybeCustodyGroupCount) {
    assertThat(
            convertNodeRecordWithFields(
                false,
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.TCP, 1234),
                new EnrField(DAS_CUSTODY_GROUP_COUNT_ENR_FIELD, Bytes.fromHexString(hexString))))
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("127.0.0.1", 1234)),
                Optional.empty(),
                Optional.empty(),
                ATTNETS,
                SYNCNETS,
                maybeCustodyGroupCount,
                Optional.empty()));
  }

  @Test
  public void shouldDecodeNfdCorrectly() {
    final Bytes4 nfd = Bytes4.fromHexString("abcdef12");
    assertThat(
            convertNodeRecordWithFields(
                false,
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.TCP, 1234),
                new EnrField(NEXT_FORK_DIGEST_ENR_FIELD, SszBytes4.of(nfd).sszSerialize())))
        .contains(
            new DiscoveryPeer(
                PUB_KEY,
                NODE_ID,
                Optional.of(new InetSocketAddress("127.0.0.1", 1234)),
                Optional.empty(),
                Optional.empty(),
                ATTNETS,
                SYNCNETS,
                Optional.empty(),
                Optional.of(nfd)));
  }

  @Test
  public void shouldExtractQuicAddressFromEnr() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            false,
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
            new EnrField(EnrField.TCP, 9000),
            new EnrField(EnrField.QUIC, 9100));
    assertThat(result).isPresent();
    assertThat(result.get().getQuicAddress()).contains(new InetSocketAddress("127.0.0.1", 9100));
    assertThat(result.get().getTcpAddress()).contains(new InetSocketAddress("127.0.0.1", 9000));
  }

  @Test
  public void shouldExtractQuicV6AddressFromEnrWhenIpv6Supported() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.TCP_V6, 9000),
            new EnrField(EnrField.QUIC_V6, 9100));
    assertThat(result).isPresent();
    assertThat(result.get().getQuicAddress()).contains(new InetSocketAddress("::1", 9100));
  }

  @Test
  public void shouldAcceptQuicOnlyIpv4Record() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            false,
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
            new EnrField(EnrField.QUIC, 9100));
    assertThat(result).isPresent();
    assertThat(result.get().getQuicAddress()).contains(new InetSocketAddress("127.0.0.1", 9100));
    assertThat(result.get().getTcpAddress()).isEmpty();
  }

  @Test
  public void shouldAcceptQuicOnlyIpv6RecordWhenIpv6Supported() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            true,
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
            new EnrField(EnrField.QUIC_V6, 9100));
    assertThat(result).isPresent();
    assertThat(result.get().getQuicAddress()).contains(new InetSocketAddress("::1", 9100));
    assertThat(result.get().getTcpAddress()).isEmpty();
  }

  @Test
  public void shouldNotConvertQuicOnlyIpv6RecordIfIpv6IsNotSupported() {
    assertThat(
            convertNodeRecordWithFields(
                false,
                new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
                new EnrField(EnrField.QUIC_V6, 9100)))
        .isEmpty();
  }

  @Test
  public void shouldHaveEmptyQuicAddressWhenNotAdvertised() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            false,
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
            new EnrField(EnrField.TCP, 9000));
    assertThat(result).isPresent();
    assertThat(result.get().getQuicAddress()).isEmpty();
  }

  private Optional<DiscoveryPeer> convertNodeRecordWithFields(
      final boolean supportsIpv6, final EnrField... fields) {
    return CONVERTER.convertToDiscoveryPeer(
        createNodeRecord(fields),
        supportsIpv6,
        SCHEMA_DEFINITIONS,
        Optional.of(MAX_CUSTODY_GROUP_COUNT));
  }

  private NodeRecord createNodeRecord(final EnrField... fields) {
    final ArrayList<EnrField> fieldList = new ArrayList<>(Arrays.asList(fields));
    fieldList.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fieldList.add(new EnrField(EnrField.PKEY_SECP256K1, PUB_KEY));
    return NodeRecordFactory.DEFAULT.createFromValues(UInt64.ZERO, fieldList);
  }

  private static Stream<Arguments> getCgcFixtures() {
    return Stream.of(
        Arguments.of("0x00", Optional.of(0)),
        Arguments.of("0x", Optional.of(0)),
        Arguments.of("0x80", Optional.of(128)),
        Arguments.of("0x8c", Optional.empty()),
        Arguments.of("0x0190", Optional.empty()),
        Arguments.of("0x0100000000", Optional.empty()));
  }
}
