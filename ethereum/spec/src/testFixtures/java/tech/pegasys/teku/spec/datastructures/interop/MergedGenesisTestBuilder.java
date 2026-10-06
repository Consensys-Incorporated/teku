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

package tech.pegasys.teku.spec.datastructures.interop;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.code.BonsaiCodeCache;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class MergedGenesisTestBuilder {
  public static ExecutionPayloadHeader createPayloadForBesuGenesis(
      final SchemaDefinitions schemaDefinitions, final String genesisConfigFile) {
    final GenesisConfig configFile = GenesisConfig.fromConfig(genesisConfigFile);
    final GenesisConfigOptions genesisConfigOptions = configFile.getConfigOptions();
    final BadBlockManager badBlockManager = new BadBlockManager();
    final ProtocolSchedule protocolSchedule =
        MainnetProtocolSchedule.fromConfig(
            genesisConfigOptions,
            MiningConfiguration.MINING_DISABLED,
            badBlockManager,
            false,
            BalConfiguration.DEFAULT,
            new NoOpMetricsSystem());
    final GenesisState genesisState =
        GenesisState.fromConfig(configFile, protocolSchedule, new BonsaiCodeCache());
    final Block genesisBlock = genesisState.getBlock();
    final BlockHeader header = genesisBlock.getHeader();

    final ExecutionPayloadHeaderSchema<?> headerSchema =
        SchemaDefinitionsBellatrix.required(schemaDefinitions).getExecutionPayloadHeaderSchema();
    return headerSchema.createExecutionPayloadHeader(
        builder ->
            builder
                .blockHash(Bytes32.wrap(header.getBlockHash().getBytes()))
                .baseFeePerGas(header.getBaseFee().orElse(Wei.ZERO).toUInt256())
                .extraData(header.getExtraData())
                .timestamp(UInt64.valueOf(header.getTimestamp()))
                .gasUsed(UInt64.valueOf(header.getGasUsed()))
                .gasLimit(UInt64.valueOf(header.getGasLimit()))
                .blockNumber(UInt64.valueOf(header.getNumber()))
                .prevRandao(header.getMixHashOrPrevRandao())
                .logsBloom(header.getLogsBloom().getBytes())
                .receiptsRoot(Bytes32.wrap(header.getReceiptsRoot().getBytes()))
                .stateRoot(Bytes32.wrap(header.getStateRoot().getBytes()))
                .feeRecipient(new Bytes20(header.getCoinbase().getBytes()))
                .parentHash(Bytes32.wrap(header.getParentHash().getBytes()))
                .transactionsRoot(headerSchema.getHeaderOfDefaultPayload().getTransactionsRoot())
                // New in Capella
                .withdrawalsRoot(
                    () ->
                        headerSchema
                            .getHeaderOfDefaultPayload()
                            .getOptionalWithdrawalsRoot()
                            .orElseThrow())
                // New in Deneb
                .blobGasUsed(() -> UInt64.ZERO)
                .excessBlobGas(() -> UInt64.ZERO)
                // New in Gloas
                .blockAccessListRoot(
                    () -> Bytes32.wrap(header.getBalHash().orElseThrow().getBytes()))
                .slotNumber(() -> UInt64.valueOf(header.getSlotNumber())));
  }
}
