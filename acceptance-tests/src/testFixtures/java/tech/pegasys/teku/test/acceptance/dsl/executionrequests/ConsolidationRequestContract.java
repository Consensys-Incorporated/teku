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

package tech.pegasys.teku.test.acceptance.dsl.executionrequests;

import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Async;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/*
 Wrapper classed based on https://github.com/lightclient/sys-asm/blob/main/src/consolidations/main.eas
*/
public class ConsolidationRequestContract {

  private final Eth1Address contractAddress;
  private final TransactionManager transactionManager;
  private final Web3j web3j;

  public ConsolidationRequestContract(
      final Eth1Address contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager) {
    this.contractAddress = contractAddress;
    this.web3j = web3j;
    this.transactionManager = transactionManager;
  }

  public SafeFuture<Integer> getExcessConsolidationRequests() {
    return SafeFuture.of(
        web3j
            .ethCall(
                Transaction.createEthCallTransaction(
                    Eth1Address.ZERO.toHexString(), contractAddress.toHexString(), ""),
                DefaultBlockParameter.valueOf("latest"))
            .sendAsync()
            .thenApply(response -> UInt256.fromHexString(response.getResult()).toInt()));
  }

  public SafeFuture<EthSendTransaction> createConsolidationRequest(
      final BLSPublicKey sourcePublicKey, final BLSPublicKey targetPublicKey) {
    final BigInteger gasPrice = BigInteger.valueOf(1_000);
    final BigInteger gasLimit = BigInteger.valueOf(1_000_000);
    final Bytes data =
        Bytes.concatenate(sourcePublicKey.toBytesCompressed(), targetPublicKey.toBytesCompressed());
    final BigInteger value = BigInteger.valueOf(2); // has to be more than current fee (0)

    return SafeFuture.of(
        Async.run(
            () ->
                transactionManager.sendTransaction(
                    gasPrice, gasLimit, contractAddress.toHexString(), data.toHexString(), value)));
  }
}
