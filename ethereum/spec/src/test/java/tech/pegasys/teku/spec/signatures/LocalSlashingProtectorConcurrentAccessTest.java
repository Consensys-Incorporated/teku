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

package tech.pegasys.teku.spec.signatures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;

public class LocalSlashingProtectorConcurrentAccessTest extends LocalSlashingProtectorTest {
  private static final Logger LOG = LogManager.getLogger();

  private final LocalSlashingProtectorConcurrentAccess slashingProtectionStorage =
      new LocalSlashingProtectorConcurrentAccess(dataWriter, baseDir, false);

  private final AsyncRunnerFactory asyncRunnerFactory =
      AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(new StubMetricsSystem()));

  final AsyncRunner asyncRunner = asyncRunnerFactory.create("LocalSlashingProtectorTest", 3);

  @Override
  protected SlashingProtector getSlashingProtector() {
    return slashingProtectionStorage;
  }

  @Test
  void cannotAccessSameValidatorConcurrently()
      throws ExecutionException, InterruptedException, TimeoutException {
    final AtomicBoolean releaseLock = new AtomicBoolean(false);

    final SafeFuture<Void> firstSigner =
        asyncRunner.runAsync(
            () -> {
              final LocalSlashingProtectionRecord record =
                  slashingProtectionStorage
                      .getSigningRecordForSigning(validator, GENESIS_VALIDATORS_ROOT)
                      .orElseThrow();
              try {
                record.lock();
                LOG.debug("LOCKED firstSigner");
                do {
                  try {
                    Thread.sleep(10);
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                } while (!releaseLock.get());
              } finally {
                record.unlock();
                LOG.debug("UNLOCK firstSigner");
              }
            });
    final LocalSlashingProtectionRecord snoopRecord =
        slashingProtectionStorage
            .getSigningRecordForSigning(validator, GENESIS_VALIDATORS_ROOT)
            .orElseThrow();
    while (!snoopRecord.getLock().isLocked()) {
      Thread.sleep(10);
    }
    LOG.debug("firstSigner has the lock");

    assertThat(snoopRecord.getLock().hasQueuedThreads()).isFalse();

    final SafeFuture<Void> secondSigner =
        asyncRunner.runAsync(
            () -> {
              final LocalSlashingProtectionRecord record =
                  slashingProtectionStorage
                      .getSigningRecordForSigning(validator, GENESIS_VALIDATORS_ROOT)
                      .orElseThrow();
              try {
                record.lock();
                LOG.debug("LOCKED secondSigner");
              } finally {
                record.unlock();
                LOG.debug("UNLOCK secondSigner");
              }
            });

    while (!snoopRecord.getLock().hasQueuedThreads()) {
      Thread.sleep(10);
    }
    LOG.debug("firstSigner waiting on acquire lock");

    assertThat(firstSigner).isNotCompleted();
    assertThat(secondSigner).isNotCompleted();

    releaseLock.set(true);
    firstSigner.get(5, TimeUnit.SECONDS);
    assertThat(firstSigner).isCompleted();
    secondSigner.get(5, TimeUnit.SECONDS);
    assertThat(secondSigner).isCompleted();

    assertThat(snoopRecord.getLock().hasQueuedThreads()).isFalse();
    assertThat(snoopRecord.getLock().isLocked()).isFalse();
  }

  @Test
  void canAccessDifferentValidatorConcurrently()
      throws ExecutionException, InterruptedException, TimeoutException {
    final AtomicBoolean releaseLock = new AtomicBoolean(false);
    final SafeFuture<Void> firstSigner =
        asyncRunner.runAsync(
            () -> {
              final LocalSlashingProtectionRecord record =
                  slashingProtectionStorage
                      .getSigningRecordForSigning(validator, GENESIS_VALIDATORS_ROOT)
                      .orElseThrow();
              try {
                record.lock();
                LOG.debug("LOCKED firstSigner");
                do {
                  try {
                    Thread.sleep(10);
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                } while (!releaseLock.get());
              } finally {
                record.unlock();
                LOG.debug("UNLOCK firstSigner");
              }
            });
    final CountDownLatch threadAcquired = new CountDownLatch(1);
    final SafeFuture<Void> secondSigner =
        asyncRunner.runAsync(
            () -> {
              threadAcquired.countDown();
              final LocalSlashingProtectionRecord record =
                  slashingProtectionStorage
                      .getSigningRecordForSigning(
                          dataStructureUtil.randomPublicKey(), GENESIS_VALIDATORS_ROOT)
                      .orElseThrow();
              try {
                record.lock();
                LOG.debug("LOCKED secondSigner");
              } finally {
                record.unlock();
                LOG.debug("UNLOCK secondSigner");
              }
            });
    threadAcquired.await();
    assertThat(firstSigner).isNotCompleted();
    secondSigner.get(5, TimeUnit.SECONDS);
    assertThat(secondSigner).isCompleted();
    releaseLock.set(true);
    firstSigner.get(5, TimeUnit.SECONDS);
    assertThat(firstSigner).isCompleted();
  }
}
