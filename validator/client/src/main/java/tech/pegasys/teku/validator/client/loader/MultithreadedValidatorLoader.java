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

package tech.pegasys.teku.validator.client.loader;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.signatures.DeletableSigner;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.api.UpdatableGraffitiProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;

/**
 * Loads validators in parallel while also limiting the number of keystores being decrypted
 * simultaneously. This is required because decrypting scrypt keystores uses a significant amount of
 * memory. If a simple `parallelStream` was used and the machine had a large number of CPUs the
 * available memory would be exhausted resulting in a crash with `OutOfMemoryError`.
 *
 * <p>Progress is reported to the logs to keep the user informed as loading a large number of keys
 * can be slow.
 */
public class MultithreadedValidatorLoader {

  public static void loadValidators(
      final OwnedValidators ownedValidators,
      final Map<BLSPublicKey, ValidatorProvider> providers,
      final GraffitiProvider defaultGraffitiProvider,
      final Function<BLSPublicKey, Optional<Bytes32>> updatableGraffitiProvider,
      final Optional<DataDirLayout> maybeDataDirLayout) {
    final int totalValidatorCount = providers.size();
    STATUS_LOG.loadingValidators(totalValidatorCount);

    final ExecutorService executorService =
        Executors.newFixedThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));
    try {
      final AtomicInteger numberOfLoadedKeys = new AtomicInteger(0);
      final List<Future<Validator>> futures =
          providers.values().stream()
              .map(
                  provider ->
                      executorService.submit(
                          () -> {
                            final BLSPublicKey publicKey = provider.getPublicKey();
                            final GraffitiProvider graffitiProvider =
                                maybeDataDirLayout
                                    .<GraffitiProvider>map(
                                        dataDirLayout ->
                                            new UpdatableGraffitiProvider(
                                                () -> updatableGraffitiProvider.apply(publicKey),
                                                defaultGraffitiProvider))
                                    .orElse(defaultGraffitiProvider);
                            final Validator validator =
                                new Validator(
                                    publicKey,
                                    new DeletableSigner(provider.createSigner()),
                                    graffitiProvider,
                                    provider.isReadOnly());
                            int loadedValidatorCount = numberOfLoadedKeys.incrementAndGet();
                            if (loadedValidatorCount % 10 == 0) {
                              STATUS_LOG.atLoadedValidatorNumber(
                                  loadedValidatorCount, totalValidatorCount);
                            }
                            return validator;
                          }))
              .toList();

      final List<Validator> addedValidators = new ArrayList<>();
      for (Future<Validator> future : futures) {
        final Validator validator = future.get();
        addedValidators.add(validator);
      }

      // Only start adding validators once we've successfully loaded all keys
      addedValidators.forEach(ownedValidators::addValidator);

      STATUS_LOG.validatorsInitialised(
          addedValidators.stream()
              .map(validator -> validator.getPublicKey().toAbbreviatedString())
              .toList());
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while attempting to load validator key files", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException("Unable to load validator key files", e);
    } finally {
      executorService.shutdownNow();
    }
  }
}
