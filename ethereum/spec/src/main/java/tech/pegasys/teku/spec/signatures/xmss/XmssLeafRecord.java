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

package tech.pegasys.teku.spec.signatures.xmss;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_ATTESTATION_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_BUILDER_BID_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_RESERVED_LEAVES;

import com.google.common.base.MoreObjects;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.XmssConstants;

/**
 * The leaves one leanXMSS key has signed at, and the signing root at each (PQ validator.md,
 * "Protection best practices"). A key must never sign two different messages at the same leaf: that
 * exposes the leaf and lets anyone forge signatures at it.
 *
 * <p>The record splits the leaves into the spec's regions:
 *
 * <ul>
 *   <li>Reserved leaves, for messages without a slot (preregistration, voluntary exits). Every
 *       signed reserved leaf is kept, and new ones are allocated in increasing order, each once.
 *   <li>Attestation leaves (one per target epoch), builder bid leaves and per-slot leaves. Each of
 *       these keeps the exact signing roots of its most recent leaves, within a window, and a
 *       watermark below which it refuses every leaf. Leaves are signed in roughly increasing order
 *       within a region, so refusing an old leaf costs at most a re-broadcast, and the record stays
 *       small enough to rewrite in full for each signature.
 * </ul>
 *
 * <p>The record is the same on every network: two networks' messages differ in their signing roots,
 * so one key used on both still must not reuse a leaf.
 */
public final class XmssLeafRecord {

  private static final byte VERSION = 1;

  /** The leaf regions, in leaf order. */
  enum Region {
    RESERVED(UInt64.ZERO, XMSS_RESERVED_LEAVES, Optional.empty()),
    // One leaf per target epoch; attesting more than 64 epochs back is never needed
    ATTESTATION(XMSS_RESERVED_LEAVES, XMSS_ATTESTATION_LEAVES, Optional.of(64L)),
    BUILDER_BID(
        XMSS_RESERVED_LEAVES.plus(XMSS_ATTESTATION_LEAVES),
        XMSS_BUILDER_BID_LEAVES,
        Optional.of(1L << 14)),
    // Proposer preferences are signed up to an epoch ahead of the other per-slot messages, so the
    // window spans 2,048 slots
    PER_SLOT(
        XMSS_RESERVED_LEAVES.plus(XMSS_ATTESTATION_LEAVES).plus(XMSS_BUILDER_BID_LEAVES),
        XmssConstants.MAX_LEAF_INDEX
            .plus(1)
            .minus(
                XMSS_RESERVED_LEAVES.plus(XMSS_ATTESTATION_LEAVES).plus(XMSS_BUILDER_BID_LEAVES)),
        Optional.of(1L << 14));

    private final UInt64 start;
    private final UInt64 size;
    private final Optional<Long> window;

    Region(final UInt64 start, final UInt64 size, final Optional<Long> window) {
      this.start = start;
      this.size = size;
      this.window = window;
    }

    boolean contains(final UInt64 leaf) {
      return leaf.isGreaterThanOrEqualTo(start) && leaf.isLessThan(start.plus(size));
    }

    static Region of(final UInt64 leaf) {
      for (final Region region : values()) {
        if (region.contains(leaf)) {
          return region;
        }
      }
      throw new IllegalArgumentException("Leaf " + leaf + " is beyond the key's leaves");
    }
  }

  /** The signed leaves of one region and the lowest leaf it may still sign. */
  private record RegionRecord(UInt64 watermark, NavigableMap<UInt64, Bytes32> signedLeaves) {

    static RegionRecord empty(final Region region) {
      return new RegionRecord(region.start, Collections.emptyNavigableMap());
    }

    Optional<RegionRecord> maySign(
        final Region region, final UInt64 leaf, final Bytes32 signingRoot) {
      if (leaf.isLessThan(watermark)) {
        return Optional.empty();
      }
      final Bytes32 recordedRoot = signedLeaves.get(leaf);
      if (recordedRoot != null) {
        return recordedRoot.equals(signingRoot) ? Optional.of(this) : Optional.empty();
      }
      final TreeMap<UInt64, Bytes32> updated = new TreeMap<>(signedLeaves);
      updated.put(leaf, signingRoot);
      return Optional.of(pruned(region, updated));
    }

    private RegionRecord pruned(final Region region, final TreeMap<UInt64, Bytes32> leaves) {
      if (region.window.isEmpty()) {
        return new RegionRecord(watermark, Collections.unmodifiableNavigableMap(leaves));
      }
      final UInt64 newestLeaf = leaves.lastKey();
      final UInt64 windowStart = newestLeaf.minusMinZero(region.window.get() - 1);
      final UInt64 newWatermark = windowStart.max(watermark);
      return new RegionRecord(
          newWatermark,
          Collections.unmodifiableNavigableMap(new TreeMap<>(leaves.tailMap(newWatermark, true))));
    }
  }

  private final Map<Region, RegionRecord> regions;

  private XmssLeafRecord(final Map<Region, RegionRecord> regions) {
    this.regions = Collections.unmodifiableMap(regions);
  }

  public static XmssLeafRecord empty() {
    final Map<Region, RegionRecord> regions = new EnumMap<>(Region.class);
    for (final Region region : Region.values()) {
      regions.put(region, RegionRecord.empty(region));
    }
    return new XmssLeafRecord(regions);
  }

  /**
   * Whether the key may sign {@code signingRoot} at {@code leaf}: the leaf is unused and at or
   * above its region's watermark, or was used for this same signing root.
   *
   * @return the record to store before signing, or empty if signing would reuse the leaf
   */
  public Optional<XmssLeafRecord> maySign(final UInt64 leaf, final Bytes32 signingRoot) {
    final Region region = Region.of(leaf);
    return regions
        .get(region)
        .maySign(region, leaf, signingRoot)
        .map(updated -> withRegion(region, updated));
  }

  /**
   * The next reserved leaf to allocate: one above the highest reserved leaf signed, so each is used
   * at most once and in increasing order. Empty once every reserved leaf has been used.
   */
  public Optional<UInt64> nextReservedLeaf() {
    final NavigableMap<UInt64, Bytes32> reserved = regions.get(Region.RESERVED).signedLeaves();
    final UInt64 next = reserved.isEmpty() ? Region.RESERVED.start : reserved.lastKey().plus(1);
    return Region.RESERVED.contains(next) ? Optional.of(next) : Optional.empty();
  }

  /** The signing root recorded at {@code leaf}, if the record still holds it. */
  public Optional<Bytes32> getSigningRoot(final UInt64 leaf) {
    return Optional.ofNullable(regions.get(Region.of(leaf)).signedLeaves().get(leaf));
  }

  private XmssLeafRecord withRegion(final Region region, final RegionRecord updated) {
    if (updated.equals(regions.get(region))) {
      return this;
    }
    final Map<Region, RegionRecord> newRegions = new EnumMap<>(regions);
    newRegions.put(region, updated);
    return new XmssLeafRecord(newRegions);
  }

  /**
   * Encodes the record: a version byte, then for each region in order its watermark, its number of
   * signed leaves and each leaf with its signing root. Integers are little-endian.
   */
  public Bytes toBytes() {
    int size = 1;
    for (final RegionRecord region : regions.values()) {
      size +=
          Long.BYTES + Integer.BYTES + region.signedLeaves().size() * (Long.BYTES + Bytes32.SIZE);
    }
    final ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    buffer.put(VERSION);
    for (final Region region : Region.values()) {
      final RegionRecord record = regions.get(region);
      buffer.putLong(record.watermark().longValue());
      buffer.putInt(record.signedLeaves().size());
      record
          .signedLeaves()
          .forEach(
              (leaf, root) -> {
                buffer.putLong(leaf.longValue());
                buffer.put(root.toArrayUnsafe());
              });
    }
    return Bytes.wrap(buffer.array());
  }

  public static XmssLeafRecord fromBytes(final Bytes bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes.toArrayUnsafe()).order(ByteOrder.LITTLE_ENDIAN);
    checkArgument(
        buffer.remaining() > 0 && buffer.get() == VERSION, "Unsupported XMSS leaf record version");
    final Map<Region, RegionRecord> regions = new EnumMap<>(Region.class);
    for (final Region region : Region.values()) {
      checkArgument(buffer.remaining() >= Long.BYTES + Integer.BYTES, "Truncated XMSS leaf record");
      final UInt64 watermark = UInt64.fromLongBits(buffer.getLong());
      final int count = buffer.getInt();
      checkArgument(
          count >= 0 && buffer.remaining() >= (long) count * (Long.BYTES + Bytes32.SIZE),
          "Truncated XMSS leaf record");
      checkArgument(
          region.contains(watermark) || watermark.equals(region.start.plus(region.size)),
          "XMSS leaf record watermark %s is outside its region",
          watermark);
      final TreeMap<UInt64, Bytes32> leaves = new TreeMap<>();
      for (int i = 0; i < count; i++) {
        final UInt64 leaf = UInt64.fromLongBits(buffer.getLong());
        final byte[] root = new byte[Bytes32.SIZE];
        buffer.get(root);
        checkArgument(
            region.contains(leaf) && leaf.isGreaterThanOrEqualTo(watermark),
            "XMSS leaf record holds leaf %s outside its region",
            leaf);
        leaves.put(leaf, Bytes32.wrap(root));
      }
      regions.put(
          region, new RegionRecord(watermark, Collections.unmodifiableNavigableMap(leaves)));
    }
    checkArgument(!buffer.hasRemaining(), "Trailing bytes in XMSS leaf record");
    return new XmssLeafRecord(regions);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof XmssLeafRecord that)) {
      return false;
    }
    return regions.equals(that.regions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(regions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("regions", regions).toString();
  }
}
