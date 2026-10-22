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

package tech.pegasys.teku.spec.datastructures.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogsBloomFilter;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Checks the block hash computation against a payload produced by a real execution client on
 * Sepolia (Gloas, mainnet preset), so the check is not circular with Teku's own payload builders.
 *
 * <p>Fixtures: the finalized envelope of slot 11411808 (beacon block root {@code 0xbe4866..94f71},
 * built by builder 0), fetched from public Sepolia beacon nodes, and the header of its execution
 * block 11969803 as returned by {@code eth_getBlockByHash} on a Sepolia execution client. The
 * payload has 110 transactions (legacy, EIP-1559, blob and set-code), 16 withdrawals, a non-empty
 * block access list and empty execution requests.
 *
 * <p>Sepolia has produced no Gloas envelope with non-empty execution requests so far, so that case
 * is covered by adding synthetic requests of every type to the real envelope: the codec output must
 * parse as Besu requests of all five types and land in the header's requests hash, so the result
 * must equal the execution client's header with only that field replaced.
 */
class ExecutionPayloadBlockHashCalculatorSepoliaTest {

  private static final UInt64 SLOT = UInt64.valueOf(11411808);
  private static final Bytes32 BEACON_BLOCK_ROOT =
      Bytes32.fromHexString("0xbe4866440976d71cf47a4af354501b5f3a2840e4f3ad92295cf7445827a94f71");
  private static final Bytes32 BLOCK_HASH =
      Bytes32.fromHexString("0xc65d9b821d3ed3edd2a7a4b6534bbb3fe2e2b0cc5c8d1c374a65adc479fd9e7d");
  private static final UInt64 BLOCK_NUMBER = UInt64.valueOf(11969803);

  private final Spec spec = SpecFactory.create("sepolia");
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitionsGloas schemaDefinitions =
      SchemaDefinitionsGloas.required(spec.atSlot(SLOT).getSchemaDefinitions());

  private ExecutionPayloadEnvelope envelope;
  private BlockHeader executionBlockHeader;

  @BeforeEach
  void setUp() throws IOException {
    envelope =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .sszDeserialize(
                Bytes.wrap(
                    Resources.toByteArray(
                        resource("sepolia-signed-execution-payload-envelope-11411808.ssz"))))
            .getMessage();
    executionBlockHeader =
        toBesuBlockHeader(
            new ObjectMapper().readTree(resource("sepolia-execution-block-header-11969803.json")));
  }

  @Test
  void computeGloasBlockHashMatchesSepoliaExecutionBlockHash() {
    // the fixture is the expected one
    assertThat(envelope.getSlot()).isEqualTo(SLOT);
    assertThat(envelope.getBeaconBlockRoot()).isEqualTo(BEACON_BLOCK_ROOT);
    assertThat(envelope.getBuilderIndex()).isEqualTo(UInt64.ZERO);
    assertThat(envelope.getPayload().getBlockNumber()).isEqualTo(BLOCK_NUMBER);
    assertThat(envelope.getPayload().getBlockHash()).isEqualTo(BLOCK_HASH);
    assertThat(envelope.getPayload().getTransactions().size()).isEqualTo(110);
    assertThat(envelope.getExecutionRequests().getDeposits()).isEmpty();

    assertThat(spec.computeExecutionPayloadBlockHash(envelope)).isEqualTo(BLOCK_HASH);
  }

  @Test
  void computeGloasBlockHashMatchesBesuWhenAddingExecutionRequestsOfEveryType() {
    // the execution client's own header fields reproduce the real block hash
    assertThat(executionBlockHeader.getHash()).isEqualTo(Hash.wrap(BLOCK_HASH));

    final ExecutionRequests requests =
        schemaDefinitions
            .getExecutionRequestsSchema()
            .createBuilder()
            .deposits(List.of(dataStructureUtil.randomDepositRequest()))
            .withdrawals(List.of(dataStructureUtil.randomWithdrawalRequest()))
            .consolidations(List.of(dataStructureUtil.randomConsolidationRequest()))
            .builderDeposits(() -> List.of(dataStructureUtil.randomBuilderDepositRequest()))
            .builderExits(() -> List.of(dataStructureUtil.randomBuilderExitRequest()))
            .build();
    final List<Bytes> encodedRequests = spec.getExecutionRequestsDataCodec(SLOT).encode(requests);
    assertThat(encodedRequests).hasSize(5);

    final Hash besuRequestsHash =
        BodyValidation.requestsHash(encodedRequests.stream().map(Request::fromBytes).toList());
    final Hash expectedBlockHash =
        BlockHeaderBuilder.fromHeader(executionBlockHeader)
            .requestsHash(besuRequestsHash)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader()
            .getHash();
    assertThat(expectedBlockHash).isNotEqualTo(Hash.wrap(BLOCK_HASH));

    final ExecutionPayloadEnvelope envelopeWithRequests =
        envelope
            .getSchema()
            .create(
                envelope.getPayload(),
                requests,
                envelope.getBuilderIndex(),
                envelope.getBeaconBlockRoot(),
                envelope.getParentBeaconBlockRoot());
    assertThat(Hash.wrap(spec.computeExecutionPayloadBlockHash(envelopeWithRequests)))
        .isEqualTo(expectedBlockHash);
  }

  private static URL resource(final String name) {
    return Resources.getResource(ExecutionPayloadBlockHashCalculatorSepoliaTest.class, name);
  }

  /** Builds the header exactly as the execution client reported it over JSON-RPC */
  private static BlockHeader toBesuBlockHeader(final JsonNode header) {
    return BlockHeaderBuilder.create()
        .parentHash(hash(header, "parentHash"))
        .ommersHash(hash(header, "sha3Uncles"))
        .coinbase(Address.fromHexString(header.get("miner").asText()))
        .stateRoot(hash(header, "stateRoot"))
        .transactionsRoot(hash(header, "transactionsRoot"))
        .receiptsRoot(hash(header, "receiptsRoot"))
        .logsBloom(new LogsBloomFilter(bytes(header, "logsBloom")))
        .difficulty(Difficulty.of(uint256(header, "difficulty")))
        .number(quantity(header, "number"))
        .gasLimit(quantity(header, "gasLimit"))
        .gasUsed(quantity(header, "gasUsed"))
        .timestamp(quantity(header, "timestamp"))
        .extraData(bytes(header, "extraData"))
        .mixHash(hash(header, "mixHash"))
        .nonce(quantity(header, "nonce"))
        .baseFee(Wei.of(uint256(header, "baseFeePerGas")))
        .withdrawalsRoot(hash(header, "withdrawalsRoot"))
        .blobGasUsed(quantity(header, "blobGasUsed"))
        .excessBlobGas(BlobGas.of(uint256(header, "excessBlobGas").toBigInteger()))
        .parentBeaconBlockRoot(Bytes32.fromHexString(header.get("parentBeaconBlockRoot").asText()))
        .requestsHash(hash(header, "requestsHash"))
        .balHash(hash(header, "blockAccessListHash"))
        .slotNumber(quantity(header, "slotNumber"))
        .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
        .buildBlockHeader();
  }

  private static Hash hash(final JsonNode header, final String field) {
    return Hash.fromHexString(header.get(field).asText());
  }

  private static Bytes bytes(final JsonNode header, final String field) {
    return Bytes.fromHexString(header.get(field).asText());
  }

  private static UInt256 uint256(final JsonNode header, final String field) {
    return UInt256.fromHexString(header.get(field).asText());
  }

  private static long quantity(final JsonNode header, final String field) {
    return uint256(header, field).toLong();
  }
}
