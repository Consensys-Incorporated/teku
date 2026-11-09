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

import static tech.pegasys.teku.infrastructure.crypto.Hash.keccak256;
import static tech.pegasys.teku.infrastructure.crypto.Hash.sha256;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogsBloomFilter;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionPayloadGloas;

public class ExecutionPayloadBlockHashCalculator {

  public static Bytes32 computeGloasBlockHash(
      final ExecutionPayloadEnvelope executionPayloadEnvelope,
      final ExecutionRequestsDataCodec executionRequestsDataCodec) {
    final ExecutionPayloadGloas payload =
        ExecutionPayloadGloas.required(executionPayloadEnvelope.getPayload());
    final BlockHeader blockHeader =
        BlockHeaderBuilder.create()
            .parentHash(Hash.wrap(payload.getParentHash()))
            .ommersHash(Hash.EMPTY_LIST_HASH)
            .coinbase(Address.wrap(payload.getFeeRecipient().getWrappedBytes()))
            .stateRoot(Hash.wrap(payload.getStateRoot()))
            .transactionsRoot(computeTransactionsRoot(payload))
            .receiptsRoot(Hash.wrap(payload.getReceiptsRoot()))
            .logsBloom(new LogsBloomFilter(payload.getLogsBloom()))
            .difficulty(Difficulty.ZERO)
            .number(toLong(payload.getBlockNumber()))
            .gasLimit(toLong(payload.getGasLimit()))
            .gasUsed(toLong(payload.getGasUsed()))
            .timestamp(toLong(payload.getTimestamp()))
            .extraData(payload.getExtraData())
            .baseFee(Wei.of(payload.getBaseFeePerGas().toBigInteger()))
            .prevRandao(payload.getPrevRandao())
            .nonce(0L)
            .withdrawalsRoot(computeWithdrawalsRoot(payload))
            .blobGasUsed(toLong(payload.getBlobGasUsed()))
            .excessBlobGas(BlobGas.of(payload.getExcessBlobGas().bigIntegerValue()))
            .parentBeaconBlockRoot(executionPayloadEnvelope.getParentBeaconBlockRoot())
            .requestsHash(
                computeRequestsHash(
                    executionRequestsDataCodec.encode(
                        executionPayloadEnvelope.getExecutionRequests())))
            .balHash(BodyValidation.balHash(payload.getBlockAccessList().getBytes()))
            .slotNumber(toLong(payload.getSlotNumber()))
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();

    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    blockHeader.writeTo(rlpOutput);
    return keccak256(rlpOutput.encoded());
  }

  private static Hash computeTransactionsRoot(final ExecutionPayloadGloas executionPayloadGloas) {
    return Util.getRootFromListOfBytes(
        executionPayloadGloas.getTransactions().stream().map(Transaction::getBytes).toList());
  }

  private static Hash computeWithdrawalsRoot(final ExecutionPayloadGloas executionPayloadGloas) {
    return BodyValidation.withdrawalsRoot(
        executionPayloadGloas.getWithdrawals().stream()
            .map(ExecutionPayloadBlockHashCalculator::toBesuWithdrawal)
            .toList());
  }

  private static org.hyperledger.besu.ethereum.core.Withdrawal toBesuWithdrawal(
      final Withdrawal withdrawal) {
    return new org.hyperledger.besu.ethereum.core.Withdrawal(
        org.apache.tuweni.units.bigints.UInt64.valueOf(withdrawal.getIndex().bigIntegerValue()),
        org.apache.tuweni.units.bigints.UInt64.valueOf(
            withdrawal.getValidatorIndex().bigIntegerValue()),
        Address.wrap(withdrawal.getAddress().getWrappedBytes()),
        GWei.of(withdrawal.getAmount().bigIntegerValue()));
  }

  private static Hash computeRequestsHash(final List<Bytes> executionRequestsData) {
    final List<Bytes> requestHashes =
        executionRequestsData.stream().map(bytes -> (Bytes) sha256(bytes)).toList();
    return Hash.wrap(sha256(Bytes.wrap(requestHashes)));
  }

  private static long toLong(final UInt64 value) {
    return value.longValue();
  }
}
