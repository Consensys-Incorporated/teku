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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;

public class InclusionListStore {
  private final LimitedMap<SlotAndBlockRoot, Map<UInt64, InclusionListEntry>> inclusionListsByKey;
  private final LimitedMap<SlotAndBlockRoot, Set<UInt64>> equivocatedValidatorIndicesByKey;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();

  public InclusionListStore(final int cacheSize) {
    checkArgument(cacheSize > 0, "Cache size must be positive");
    inclusionListsByKey = LimitedMap.createSynchronizedNatural(cacheSize);
    equivocatedValidatorIndicesByKey = LimitedMap.createSynchronizedNatural(cacheSize);
  }

  public void processInclusionList(
      final SignedInclusionList signedInclusionList, final boolean timely) {
    writeLock.lock();
    try {
      final InclusionList inclusionList = signedInclusionList.getMessage();
      final SlotAndBlockRoot key = keyFor(inclusionList);
      final Map<UInt64, InclusionListEntry> inclusionLists =
          inclusionListsByKey.computeIfAbsent(key, __ -> new LinkedHashMap<>());
      final UInt64 validatorIndex = inclusionList.getValidatorIndex();
      if (!inclusionLists.containsKey(validatorIndex)) {
        inclusionLists.put(validatorIndex, new InclusionListEntry(signedInclusionList, timely));
        return;
      }

      // Mark the validator as an equivocator if it published a different inclusion list. Either
      // way the first message and its timeliness are retained.
      final InclusionListEntry storedEntry = inclusionLists.get(validatorIndex);
      if (!storedEntry.signedInclusionList().getMessage().equals(inclusionList)) {
        equivocatedValidatorIndicesByKey
            .computeIfAbsent(key, __ -> new HashSet<>())
            .add(validatorIndex);
      }
    } finally {
      writeLock.unlock();
    }
  }

  /** Returns an unfiltered snapshot, including untimely entries and entries from equivocators. */
  public Optional<Map<UInt64, InclusionListEntry>> getInclusionLists(final SlotAndBlockRoot key) {
    readLock.lock();
    try {
      return Optional.ofNullable(inclusionListsByKey.get(key)).map(Map::copyOf);
    } finally {
      readLock.unlock();
    }
  }

  /** Returns the timely inclusion lists from non-equivocating validators for the given key. */
  public List<InclusionList> getTimelyInclusionLists(final SlotAndBlockRoot key) {
    readLock.lock();
    try {
      final Set<UInt64> equivocators = equivocatedValidatorIndicesByKey.getOrDefault(key, Set.of());
      return inclusionListsByKey.getOrDefault(key, Map.of()).entrySet().stream()
          .filter(entry -> !equivocators.contains(entry.getKey()))
          .map(Map.Entry::getValue)
          .filter(InclusionListEntry::timely)
          .map(entry -> entry.signedInclusionList().getMessage())
          .toList();
    } finally {
      readLock.unlock();
    }
  }

  public boolean isInclusionListEquivocator(
      final SlotAndBlockRoot key, final UInt64 validatorIndex) {
    readLock.lock();
    try {
      return equivocatedValidatorIndicesByKey.getOrDefault(key, Set.of()).contains(validatorIndex);
    } finally {
      readLock.unlock();
    }
  }

  public List<Bytes> getInclusionListTransactions(
      final SlotAndBlockRoot key, final boolean onlyTimely) {
    readLock.lock();
    try {
      final Set<UInt64> equivocators = equivocatedValidatorIndicesByKey.getOrDefault(key, Set.of());
      return inclusionListsByKey.getOrDefault(key, Map.of()).entrySet().stream()
          .filter(entry -> !equivocators.contains(entry.getKey()))
          .map(Map.Entry::getValue)
          .filter(entry -> !onlyTimely || entry.timely())
          .flatMap(entry -> entry.signedInclusionList().getMessage().getTransactions().stream())
          .map(SszByteListImpl::getBytes)
          .distinct()
          .toList();
    } finally {
      readLock.unlock();
    }
  }

  public SszBitvector getInclusionListBits(
      final IntList committee, final SlotAndBlockRoot key, final boolean onlyTimely) {
    readLock.lock();
    try {
      final Map<UInt64, InclusionListEntry> inclusionLists =
          inclusionListsByKey.getOrDefault(key, Map.of());
      final Set<UInt64> equivocators = equivocatedValidatorIndicesByKey.getOrDefault(key, Set.of());
      return SszBitvectorSchema.create(committee.size())
          .ofBits(
              IntStream.range(0, committee.size())
                  .filter(
                      position -> {
                        final UInt64 validatorIndex = UInt64.valueOf(committee.getInt(position));
                        final InclusionListEntry entry = inclusionLists.get(validatorIndex);
                        return entry != null
                            && !equivocators.contains(validatorIndex)
                            && (!onlyTimely || entry.timely());
                      })
                  .toArray());
    } finally {
      readLock.unlock();
    }
  }

  private SlotAndBlockRoot keyFor(final InclusionList inclusionList) {
    return new SlotAndBlockRoot(inclusionList.getSlot(), inclusionList.getDependentRoot());
  }
}
