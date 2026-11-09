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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionRequestsService implements AutoCloseable {

  // Increase the poll rate for tx receipts but keep the default 10 min timeout.
  private static final int POLL_INTERVAL_MILLIS = 2000;
  private static final int MAX_POLL_ATTEMPTS = 300;

  //  private final WithdrawalRequestTransactionSender sender;
  private final OkHttpClient httpClient;
  private final ScheduledExecutorService executorService;
  private final Web3j web3j;
  private final WithdrawalRequestContract withdrawalRequestContract;
  private final ConsolidationRequestContract consolidationRequestContract;

  public ExecutionRequestsService(
      final String eth1NodeUrl,
      final Credentials eth1Credentials,
      final Eth1Address withdrawalRequestAddress,
      final Eth1Address consolidationRequestAddress) {
    this.httpClient = new OkHttpClient.Builder().connectionPool(new ConnectionPool()).build();
    this.executorService =
        Executors.newScheduledThreadPool(
            1,
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("web3j-executionRequests-%d")
                .build());
    this.web3j = Web3j.build(new HttpService(eth1NodeUrl, httpClient), 1000, executorService);

    final TransactionManager transactionManager =
        new FastRawTransactionManager(
            web3j,
            eth1Credentials,
            new PollingTransactionReceiptProcessor(web3j, POLL_INTERVAL_MILLIS, MAX_POLL_ATTEMPTS));

    this.withdrawalRequestContract =
        new WithdrawalRequestContract(withdrawalRequestAddress, web3j, transactionManager);
    this.consolidationRequestContract =
        new ConsolidationRequestContract(consolidationRequestAddress, web3j, transactionManager);
  }

  @Override
  public void close() {
    web3j.shutdown();
    httpClient.dispatcher().executorService().shutdownNow();
    httpClient.connectionPool().evictAll();
    executorService.shutdownNow();
  }

  public SafeFuture<TransactionReceipt> createWithdrawalRequest(
      final BLSPublicKey publicKey, final UInt64 amount) {
    // Sanity check that we can interact with the contract
    Waiter.waitFor(
        () ->
            assertThat(withdrawalRequestContract.getExcessWithdrawalRequests().get()).isEqualTo(0));

    return withdrawalRequestContract
        .createWithdrawalRequest(publicKey, amount)
        .thenCompose(
            response -> {
              final String txHash = response.getResult();
              waitForSuccessfulTransaction(txHash);
              return getTransactionReceipt(txHash);
            });
  }

  public SafeFuture<TransactionReceipt> createConsolidationRequest(
      final BLSPublicKey sourceValidatorPubkey, final BLSPublicKey targetValidatorPubkey) {
    // Sanity check that we can interact with the contract
    Waiter.waitFor(
        () ->
            assertThat(consolidationRequestContract.getExcessConsolidationRequests().get())
                .isEqualTo(0));

    return consolidationRequestContract
        .createConsolidationRequest(sourceValidatorPubkey, targetValidatorPubkey)
        .thenCompose(
            response -> {
              final String txHash = response.getResult();
              waitForSuccessfulTransaction(txHash);
              return getTransactionReceipt(txHash);
            });
  }

  private SafeFuture<TransactionReceipt> getTransactionReceipt(final String txHash) {
    return SafeFuture.of(
        web3j
            .ethGetTransactionReceipt(txHash)
            .sendAsync()
            .thenApply(EthGetTransactionReceipt::getTransactionReceipt)
            .thenApply(Optional::orElseThrow));
  }

  private void waitForSuccessfulTransaction(final String txHash) {
    Waiter.waitFor(
        () -> {
          final TransactionReceipt transactionReceipt =
              web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElseThrow();
          if (!"0x1".equals(transactionReceipt.getStatus())) {
            throw new RuntimeException("Transaction failed");
          }
        },
        1,
        TimeUnit.MINUTES);
  }
}
