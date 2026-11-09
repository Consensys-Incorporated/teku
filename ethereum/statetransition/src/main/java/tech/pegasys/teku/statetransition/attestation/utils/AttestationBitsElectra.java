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

package tech.pegasys.teku.statetransition.attestation.utils;

import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.statetransition.attestation.PooledAttestation;

class AttestationBitsElectra implements AttestationBits {

  private final SszBitlistSchema<?> aggregationBitsSchema;
  private final SszBitvectorSchema<?> committeeBitsSchema;
  private final Int2IntMap committeesSize;

  private Int2ObjectMap<BitSet> committeeAggregationBitsMap;
  private BitSet committeeBits;

  private SszBitlist cachedAggregationSszBits = null;
  private SszBitvector cachedCommitteeSszBits = null;

  AttestationBitsElectra(
      final SszBitlist initialAggregationBits,
      final SszBitvector initialCommitteeBits,
      final Int2IntMap committeesSize) {
    this.aggregationBitsSchema = initialAggregationBits.getSchema();
    this.committeeBitsSchema = initialCommitteeBits.getSchema();
    this.committeesSize = Objects.requireNonNull(committeesSize, "committeesSize cannot be null");
    this.committeeBits = initialCommitteeBits.getAsBitSet();
    this.committeeAggregationBitsMap =
        parseAggregationBits(initialAggregationBits, this.committeeBits, this.committeesSize);
  }

  private AttestationBitsElectra(
      final SszBitlistSchema<?> aggregationBitsSchema,
      final SszBitvectorSchema<?> committeeBitsSchema,
      final Int2IntMap committeesSize,
      final Int2ObjectMap<BitSet> committeeAggregationBitsMap,
      final BitSet committeeBits) {
    this.aggregationBitsSchema = aggregationBitsSchema;
    this.committeeBitsSchema = committeeBitsSchema;
    this.committeesSize = Objects.requireNonNull(committeesSize, "committeesSize cannot be null");
    this.committeeBits = committeeBits;
    this.committeeAggregationBitsMap = committeeAggregationBitsMap;
  }

  static AttestationBits fromAttestationSchema(
      final AttestationSchema<?> attestationSchema, final Int2IntMap committeesSize) {
    final SszBitlist emptyAggregationBits = attestationSchema.createEmptyAggregationBits();
    final SszBitvector emptyCommitteeBits =
        attestationSchema
            .createEmptyCommitteeBits()
            .orElseThrow(
                () -> new IllegalStateException("Electra schema must provide committee bits"));
    return new AttestationBitsElectra(emptyAggregationBits, emptyCommitteeBits, committeesSize);
  }

  private static Int2ObjectMap<BitSet> parseAggregationBits(
      final SszBitlist aggregationBits,
      final BitSet committeeIndices,
      final Int2IntMap committeesSizeMap) {
    final Int2ObjectMap<BitSet> result = new Int2ObjectOpenHashMap<>();

    int currentOffset = 0;
    for (int committeeIndex = committeeIndices.nextSetBit(0);
        committeeIndex >= 0;
        committeeIndex = committeeIndices.nextSetBit(committeeIndex + 1)) {

      final int committeeSize = committeesSizeMap.getOrDefault(committeeIndex, 0);
      if (committeeSize == 0) {
        throw new IllegalArgumentException(
            "Committee size for committee " + committeeIndex + " not found");
      }
      final BitSet committeeBits =
          aggregationBits.getAsBitSet(currentOffset, currentOffset + committeeSize);
      result.put(committeeIndex, committeeBits);
      currentOffset += committeeSize;
    }
    return result;
  }

  @Override
  public void or(final AttestationBits other) {
    final AttestationBitsElectra otherElectra = requiresElectra(other);

    performMerge(otherElectra.committeeBits, otherElectra.committeeAggregationBitsMap, false);
  }

  @Override
  public boolean aggregateWith(final PooledAttestation other) {
    final AttestationBitsElectra otherElectra = requiresElectra(other.bits());

    if (other.isSingleAttestation()) {
      final int committeeBit = otherElectra.getSingleCommitteeIndex();
      final int aggregationBit =
          otherElectra.committeeAggregationBitsMap.get(committeeBit).length() - 1;
      return aggregateWithSingleAttestation(committeeBit, aggregationBit);
    } else {
      return performMerge(
          otherElectra.committeeBits, otherElectra.committeeAggregationBitsMap, true);
    }
  }

  @Override
  public void or(final Attestation other) {
    final BitSet otherCommitteeBits = other.getCommitteeBitsRequired().getAsBitSet();
    final Int2ObjectMap<BitSet> otherParsedAggregationMap =
        parseAggregationBits(other.getAggregationBits(), otherCommitteeBits, this.committeesSize);
    performMerge(otherCommitteeBits, otherParsedAggregationMap, false);
  }

  private static Int2ObjectMap<BitSet> cloneCommitteeAggregationBitsMap(
      final Int2ObjectMap<BitSet> committeeAggregationBitsMap) {
    final Int2ObjectMap<BitSet> clonedMap = new Int2ObjectOpenHashMap<>();
    for (final Int2ObjectMap.Entry<BitSet> entry :
        committeeAggregationBitsMap.int2ObjectEntrySet()) {
      clonedMap.put(entry.getIntKey(), (BitSet) entry.getValue().clone());
    }
    return clonedMap;
  }

  private boolean aggregateWithSingleAttestation(
      final int otherCommitteeBit, final int otherAggregationBit) {
    final BitSet thisAggregationBitsForCommittee =
        committeeAggregationBitsMap.get(otherCommitteeBit);

    if (thisAggregationBitsForCommittee != null
        && thisAggregationBitsForCommittee.get(otherAggregationBit)) {
      // Intersection found, cannot merge
      return false;
    }

    if (thisAggregationBitsForCommittee != null) {
      // committee present, just add the aggregation bit
      thisAggregationBitsForCommittee.set(otherAggregationBit);
    } else {
      // committee is not present, set the committee bit and create a new BitSet for aggregation
      // bits

      this.committeeBits.set(otherCommitteeBit);
      final BitSet newAggregationBits = new BitSet(committeesSize.get(otherCommitteeBit));
      newAggregationBits.set(otherAggregationBit);
      committeeAggregationBitsMap.put(otherCommitteeBit, newAggregationBits);
    }

    invalidateCache();
    return true;
  }

  private boolean performMerge(
      final BitSet otherCommitteeBits,
      final Int2ObjectMap<BitSet> otherCommitteeAggregationBitsMap,
      final boolean isAggregation) {
    final BitSet mergedCommitteeBits = (BitSet) this.committeeBits.clone();
    mergedCommitteeBits.or(otherCommitteeBits);

    final Int2ObjectMap<BitSet> targetAggregationBitsMap;

    if (isAggregation) {
      // If aggregating, we need to work on copies
      targetAggregationBitsMap = cloneCommitteeAggregationBitsMap(this.committeeAggregationBitsMap);
    } else {
      // if not aggregating, we can modify in place
      targetAggregationBitsMap = this.committeeAggregationBitsMap;
    }

    for (int committeeIndex = mergedCommitteeBits.nextSetBit(0);
        committeeIndex >= 0;
        committeeIndex = mergedCommitteeBits.nextSetBit(committeeIndex + 1)) {

      final boolean inThis = this.committeeBits.get(committeeIndex);
      final boolean inOther = otherCommitteeBits.get(committeeIndex);

      if (inThis && inOther) {
        final BitSet otherAggregationBitsForCommittee =
            otherCommitteeAggregationBitsMap.get(committeeIndex);
        final BitSet targetAggregationBitsForCommittee =
            targetAggregationBitsMap.get(committeeIndex);

        if (isAggregation) {
          // For intersection check, use the original bits of 'this'
          final BitSet thisAggregationBitsForCommittee =
              this.committeeAggregationBitsMap.get(committeeIndex);
          if (thisAggregationBitsForCommittee != null
              && thisAggregationBitsForCommittee.intersects(otherAggregationBitsForCommittee)) {
            return false;
          }
        }

        targetAggregationBitsForCommittee.or(otherAggregationBitsForCommittee);

      } else if (inOther) {
        // Committee only in 'other'.
        final BitSet otherDataForCommittee = otherCommitteeAggregationBitsMap.get(committeeIndex);

        targetAggregationBitsMap.put(committeeIndex, (BitSet) otherDataForCommittee.clone());
      }
      // Committee only in 'this', do nothing.
    }

    this.committeeBits = mergedCommitteeBits;
    if (isAggregation) {
      this.committeeAggregationBitsMap = targetAggregationBitsMap;
    }

    invalidateCache();
    return true;
  }

  @Override
  public boolean isSuperSetOf(final Attestation other) {
    final BitSet otherCommitteeBits = other.getCommitteeBitsRequired().getAsBitSet();
    return isSuperSetOf(
        otherCommitteeBits,
        () ->
            parseAggregationBits(
                other.getAggregationBits(), otherCommitteeBits, this.committeesSize));
  }

  @Override
  public boolean isSuperSetOf(final PooledAttestation other) {
    final AttestationBitsElectra otherElectra = requiresElectra(other.bits());
    if (other.isSingleAttestation()) {
      final int committeeBit = otherElectra.getSingleCommitteeIndex();
      final int aggregationBit =
          otherElectra.committeeAggregationBitsMap.get(committeeBit).length() - 1;
      return isSuperSetOfSingleAttestation(committeeBit, aggregationBit);
    } else {
      return isSuperSetOf(
          otherElectra.committeeBits, () -> otherElectra.committeeAggregationBitsMap);
    }
  }

  private boolean isSuperSetOfSingleAttestation(
      final int otherCommitteeBit, final int otherAggregationBit) {
    final BitSet thisAggregationBitsForCommittee =
        committeeAggregationBitsMap.get(otherCommitteeBit);

    if (thisAggregationBitsForCommittee == null) {
      return false; // No bits for this committee
    }

    return thisAggregationBitsForCommittee.get(otherAggregationBit);
  }

  private boolean isSuperSetOf(
      final BitSet otherCommitteeBits,
      final Supplier<Int2ObjectMap<BitSet>> otherCommitteeAggregationBitsMapSupplier) {

    final BitSet committeeIntersection = (BitSet) this.committeeBits.clone();
    committeeIntersection.and(otherCommitteeBits);
    if (!committeeIntersection.equals(otherCommitteeBits)) {
      return false;
    }

    final Int2ObjectMap<BitSet> otherCommitteeAggregationBitsMap =
        otherCommitteeAggregationBitsMapSupplier.get();

    for (int committeeIndex = otherCommitteeBits.nextSetBit(0);
        committeeIndex >= 0;
        committeeIndex = otherCommitteeBits.nextSetBit(committeeIndex + 1)) {

      final BitSet thisAggregationBitsForCommittee =
          this.committeeAggregationBitsMap.get(committeeIndex);
      final BitSet otherAggregationBitsForCommittee =
          otherCommitteeAggregationBitsMap.get(committeeIndex);

      if (thisAggregationBitsForCommittee == null) {
        return false;
      }
      if (otherAggregationBitsForCommittee == null || otherAggregationBitsForCommittee.isEmpty()) {
        continue;
      }

      final BitSet otherBitsNotInThis = (BitSet) otherAggregationBitsForCommittee.clone();
      otherBitsNotInThis.andNot(thisAggregationBitsForCommittee);
      if (!otherBitsNotInThis.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public SszBitlist getAggregationSszBits() {
    if (cachedAggregationSszBits != null) {
      return cachedAggregationSszBits;
    }
    final List<Integer> committeeIndicesInOrder = new ArrayList<>();
    for (int i = committeeBits.nextSetBit(0); i >= 0; i = committeeBits.nextSetBit(i + 1)) {
      committeeIndicesInOrder.add(i);
    }
    // No explicit sort needed as BitSet.nextSetBit() iterates in ascending order.

    int totalBitlistSize = 0;
    for (final int committeeIndex : committeeIndicesInOrder) {
      totalBitlistSize += this.committeesSize.getOrDefault(committeeIndex, 0);
    }

    final BitSet combinedAggregationBits = new BitSet(totalBitlistSize);
    int currentOffset = 0;
    for (final int committeeIndex : committeeIndicesInOrder) {
      final BitSet committeeBitsData = this.committeeAggregationBitsMap.get(committeeIndex);
      final int committeeSize = this.committeesSize.getOrDefault(committeeIndex, 0);

      if (committeeBitsData != null && committeeSize > 0) {
        for (int bitIndex = committeeBitsData.nextSetBit(0);
            bitIndex >= 0 && bitIndex < committeeSize;
            bitIndex = committeeBitsData.nextSetBit(bitIndex + 1)) {
          combinedAggregationBits.set(currentOffset + bitIndex);
        }
      }
      currentOffset += committeeSize;
    }
    cachedAggregationSszBits =
        aggregationBitsSchema.wrapBitSet(totalBitlistSize, combinedAggregationBits);
    return cachedAggregationSszBits;
  }

  @Override
  public SszBitvector getCommitteeSszBits() {
    if (cachedCommitteeSszBits == null) {
      cachedCommitteeSszBits =
          committeeBitsSchema.wrapBitSet(
              committeeBitsSchema.getLength(), (BitSet) this.committeeBits.clone());
    }
    return cachedCommitteeSszBits;
  }

  private void invalidateCache() {
    this.cachedAggregationSszBits = null;
    this.cachedCommitteeSszBits = null;
  }

  @Override
  public Int2IntMap getCommitteesSize() {
    return committeesSize;
  }

  @Override
  public boolean requiresCommitteeBits() {
    return true;
  }

  @Override
  public AttestationBits copy() {
    return new AttestationBitsElectra(
        aggregationBitsSchema,
        committeeBitsSchema,
        committeesSize,
        cloneCommitteeAggregationBitsMap(committeeAggregationBitsMap),
        (BitSet) committeeBits.clone());
  }

  @Override
  public int getBitCount() {
    return committeeAggregationBitsMap.values().stream().mapToInt(BitSet::cardinality).sum();
  }

  @Override
  public boolean isExclusivelyFromCommittee(final int committeeIndex) {
    return committeeAggregationBitsMap.size() == 1
        && committeeAggregationBitsMap.containsKey(committeeIndex);
  }

  @Override
  public boolean isFromCommittee(final int committeeIndex) {
    return committeeAggregationBitsMap.containsKey(committeeIndex);
  }

  @Override
  public int getSingleCommitteeIndex() {
    return committeeBits.length() - 1;
  }

  @Override
  public IntStream streamCommitteeIndices() {
    return committeeBits.stream();
  }

  @Override
  public String toString() {
    long totalSetBits = 0;
    if (committeeAggregationBitsMap != null) {
      for (final BitSet bitSet : committeeAggregationBitsMap.values()) {
        if (bitSet != null) {
          totalSetBits += bitSet.cardinality();
        }
      }
    }
    return MoreObjects.toStringHelper(this)
        .add("committeeBits", committeeBits.cardinality())
        .add("committeeAggregationBitsMap", totalSetBits)
        .add("committeesSize", committeesSize.size())
        .add("sszBitsCached", cachedAggregationSszBits != null || cachedCommitteeSszBits != null)
        .toString();
  }

  static AttestationBitsElectra requiresElectra(final AttestationBits aggregator) {
    if (!(aggregator instanceof AttestationBitsElectra aggregatorElectra)) {
      throw new IllegalArgumentException(
          "AttestationBitsAggregator required to be Electra but was: "
              + aggregator.getClass().getSimpleName());
    }
    return aggregatorElectra;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof AttestationBitsElectra that)) {
      return false;
    }
    return this.committeeBits.equals(that.committeeBits)
        && Objects.equals(committeeAggregationBitsMap, that.committeeAggregationBitsMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(committeeBits, committeeAggregationBitsMap);
  }
}
