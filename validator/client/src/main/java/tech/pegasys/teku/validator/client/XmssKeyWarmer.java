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

package tech.pegasys.teku.validator.client;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.signatures.xmss.XmssLeafLayout;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

/**
 * Builds the leanXMSS bottom trees the owned validators sign from before they are needed. A
 * signature in a tree the key does not hold yet first builds it, which takes seconds and would make
 * the duty late. So this prepares every key for the current slot, and for the slot {@link
 * #LOOKAHEAD_SLOTS} ahead whenever that slot uses a tree the current one does not: the per-slot
 * leaves move to a new tree every 8192 slots, and the attestation leaves every 65536 epochs.
 *
 * <p>Keys that already hold the trees, for example from their saved tree cache, have nothing to
 * build. A tree built here is saved to that cache by the key loader, so it is built once.
 *
 * <p>Only a few keys are prepared at once, so warming up leaves threads free to sign duties.
 */
public class XmssKeyWarmer implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  /**
   * How far ahead of a new bottom tree's first slot to start building it. 1000 keys on two threads
   * take about 100 slots at a few seconds per tree.
   */
  public static final int LOOKAHEAD_SLOTS = 256;

  private final Spec spec;
  private final OwnedValidators validators;
  private final TimeProvider timeProvider;
  private final int lookaheadSlots;
  private final ThrottlingTaskQueue taskQueue;

  // The bottom trees every key has been asked to prepare
  private Set<Long> warmedTrees = Set.of();

  public XmssKeyWarmer(
      final Spec spec, final OwnedValidators validators, final TimeProvider timeProvider) {
    this(spec, validators, timeProvider, LOOKAHEAD_SLOTS, defaultMaxKeysAtOnce());
  }

  @VisibleForTesting
  XmssKeyWarmer(
      final Spec spec,
      final OwnedValidators validators,
      final TimeProvider timeProvider,
      final int lookaheadSlots,
      final int maxKeysAtOnce) {
    this.spec = spec;
    this.validators = validators;
    this.timeProvider = timeProvider;
    this.lookaheadSlots = lookaheadSlots;
    this.taskQueue = ThrottlingTaskQueue.create(maxKeysAtOnce, Integer.MAX_VALUE);
  }

  /** A quarter of the cores: preparing a key keeps one thread busy for seconds. */
  private static int defaultMaxKeysAtOnce() {
    return Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    final UInt64 slotAhead = slot.plus(lookaheadSlots);
    final List<Long> currentTrees = XmssLeafLayout.bottomTreesAt(spec, slot);
    final List<Long> treesAhead = XmssLeafLayout.bottomTreesAt(spec, slotAhead);
    // The current slot first: its trees are needed now
    if (!warmedTrees.containsAll(currentTrees)) {
      warmUp(slot);
    }
    if (!warmedTrees.containsAll(treesAhead) && !currentTrees.containsAll(treesAhead)) {
      warmUp(slotAhead);
    }
    final Set<Long> warmed = new HashSet<>(currentTrees);
    warmed.addAll(treesAhead);
    warmedTrees = warmed;
  }

  @Override
  public synchronized void onValidatorsAdded() {
    // New keys have no trees yet. Keys that do are asked again on the next slot, which costs
    // nothing
    warmedTrees = Set.of();
  }

  private void warmUp(final UInt64 slot) {
    final List<XmssSigner> signers =
        validators.getValidators().stream()
            .flatMap(validator -> validator.getXmssSigner().stream())
            .toList();
    if (signers.isEmpty()) {
      return;
    }
    final UInt64 startMillis = timeProvider.getTimeInMillis();
    SafeFuture.collectAll(signers.stream().map(signer -> prepare(signer, slot)))
        .thenAccept(
            treesPerKey -> {
              final int trees = treesPerKey.stream().mapToInt(Integer::intValue).sum();
              if (trees > 0) {
                LOG.info(
                    "Built {} leanXMSS bottom trees for {} validators to sign at slot {}, in {}"
                        + " ms",
                    trees,
                    signers.size(),
                    slot,
                    timeProvider.getTimeInMillis().minusMinZero(startMillis));
              }
            })
        .finishError(LOG);
  }

  private SafeFuture<Integer> prepare(final XmssSigner signer, final UInt64 slot) {
    return taskQueue
        .queueTask(() -> signer.prepareForSlot(slot))
        .exceptionally(
            error -> {
              LOG.warn(
                  "Unable to prepare leanXMSS key {} to sign at slot {}. Its first signature"
                      + " there will be slow.",
                  signer.getPublicKey(),
                  slot,
                  error);
              return 0;
            });
  }
}
