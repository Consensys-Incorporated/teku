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

package tech.pegasys.teku.statetransition.datacolumns;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.db.DelayedDasDb;
import tech.pegasys.teku.statetransition.datacolumns.util.StubAsync;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DasCustodyStand {

  public static Builder builder(final Spec spec) {
    return new Builder().withSpec(spec);
  }

  final StubAsync stubAsync = new StubAsync();

  public final Spec spec;

  public final CanonicalBlockResolverStub blockResolver;

  public final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;
  public final DataColumnSidecarDBStub db;
  public final DataColumnSidecarDbAccessor dbAccessor;
  public final CustodyGroupCountManager custodyGroupCountManager;

  public final DataColumnSidecarCustodyImpl custody;

  public final DataStructureUtil dataStructureUtil;

  private final List<SlotEventsChannel> slotListeners = new CopyOnWriteArrayList<>();
  private final List<FinalizedCheckpointChannel> finalizedListeners = new CopyOnWriteArrayList<>();

  private UInt64 currentSlot = UInt64.ZERO;

  private DasCustodyStand(
      final Spec spec,
      final int totalCustodyGroupCount,
      final int samplingGroupCount,
      final Optional<Duration> asyncDbDelay,
      final Optional<Duration> asyncBlockResolverDelay) {
    this.spec = spec;
    this.blockResolver = new CanonicalBlockResolverStub(spec);
    final CanonicalBlockResolver asyncBlockResolver =
        asyncBlockResolverDelay
            .map(
                delay ->
                    (CanonicalBlockResolver)
                        new DelayedCanonicalBlockResolver(
                            this.blockResolver, stubAsync.getStubAsyncRunner(), delay))
            .orElse(this.blockResolver);
    this.minCustodyPeriodSlotCalculator = MinCustodyPeriodSlotCalculator.createFromSpec(spec);
    this.db = new DataColumnSidecarDBStub();
    final DataColumnSidecarDB asyncDb =
        asyncDbDelay
            .map(
                dbDelay ->
                    (DataColumnSidecarDB)
                        new DelayedDasDb(this.db, stubAsync.getStubAsyncRunner(), dbDelay))
            .orElse(this.db);

    this.dbAccessor = DataColumnSidecarDbAccessor.builder(asyncDb).spec(spec).build();
    this.custodyGroupCountManager =
        createCustodyGroupCountManager(totalCustodyGroupCount, samplingGroupCount);
    this.custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            asyncBlockResolver,
            dbAccessor,
            minCustodyPeriodSlotCalculator,
            custodyGroupCountManager);
    subscribeToSlotEvents(this.custody);
    subscribeToFinalizedEvents(this.custody);

    final DataStructureUtil util = new DataStructureUtil(0, spec);
    final BLSSignature singleSignature = util.randomSignature();
    final BLSPublicKey singlePubKey = util.randomPublicKey();
    this.dataStructureUtil =
        util.withSignatureGenerator(__ -> singleSignature).withPubKeyGenerator(() -> singlePubKey);
  }

  public void advanceTimeGradually(final Duration delta) {
    stubAsync.advanceTimeGradually(delta);
  }

  public void advanceTimeGraduallyUntilAllDone(final Duration maxAdvancePeriod) {
    stubAsync.advanceTimeGraduallyUntilAllDone(maxAdvancePeriod);
  }

  public SignedBeaconBlock createBlockWithBlobs(final int slot) {
    return createBlock(slot, 3);
  }

  public SignedBeaconBlock createBlockWithoutBlobs(final int slot) {
    return createBlock(slot, 0);
  }

  public SignedBeaconBlock createBlock(final int slot, final int blobCount) {
    final UInt64 slotU = UInt64.valueOf(slot);
    final BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(blobCount);
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(slotU, beaconBlockBody);
    return dataStructureUtil.signedBlock(block);
  }

  public DataColumnSidecar createSidecar(final SignedBeaconBlock block, final int column) {
    return dataStructureUtil.randomDataColumnSidecar(block.asHeader(), UInt64.valueOf(column));
  }

  public boolean hasBlobs(final BeaconBlock block) {
    return block
        .getBody()
        .toVersionDeneb()
        .map(b -> !b.getBlobKzgCommitments().isEmpty())
        .orElse(false);
  }

  public Collection<UInt64> getCustodyColumnIndices() {
    return custodyGroupCountManager.getCustodyColumnIndices();
  }

  public Optional<UInt64> getMinCustodySlot() {
    return minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(currentSlot);
  }

  public List<DataColumnSidecar> createCustodyColumnSidecars(final SignedBeaconBlock block) {
    if (hasBlobs(block.getBeaconBlock().orElseThrow())) {
      final Collection<UInt64> custodyColumnIndices = getCustodyColumnIndices();
      return custodyColumnIndices.stream()
          .map(colIndex -> createSidecar(block, colIndex.intValue()))
          .toList();
    } else {
      return Collections.emptyList();
    }
  }

  public void subscribeToSlotEvents(final SlotEventsChannel subscriber) {
    slotListeners.add(subscriber);
  }

  public void incCurrentSlot(final int delta) {
    setCurrentSlot(getCurrentSlot().intValue() + delta);
  }

  public void setCurrentSlot(final int slot) {
    if (currentSlot.isGreaterThan(slot)) {
      throw new IllegalArgumentException("New slot " + slot + " < " + currentSlot);
    }
    currentSlot = UInt64.valueOf(slot);
    slotListeners.forEach(l -> l.onSlot(UInt64.valueOf(slot)));
  }

  public UInt64 getCurrentSlot() {
    return currentSlot;
  }

  public void subscribeToFinalizedEvents(final FinalizedCheckpointChannel subscriber) {
    finalizedListeners.add(subscriber);
  }

  public void setFinalizedEpoch(final int epoch) {
    final Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.valueOf(epoch), Bytes32.ZERO);
    finalizedListeners.forEach(l -> l.onNewFinalizedCheckpoint(finalizedCheckpoint, false));
  }

  public static class Builder {
    private Spec spec;
    private Integer totalCustodyGroupCount;
    private Integer samplingGroupCount;
    private Optional<Duration> asyncDbDelay = Optional.empty();
    private Optional<Duration> asyncBlockResolverDelay = Optional.empty();

    public Builder withSpec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder withTotalCustodySubnetCount(final Integer totalCustodySubnetCount) {
      this.totalCustodyGroupCount = totalCustodySubnetCount;
      return this;
    }

    public Builder withSamplingGroupCount(final Integer samplingGroupCount) {
      this.samplingGroupCount = samplingGroupCount;
      return this;
    }

    public Builder withAsyncDb(final Duration asyncDbDelay) {
      this.asyncDbDelay = Optional.ofNullable(asyncDbDelay);
      return this;
    }

    public Builder withAsyncBlockResolver(final Duration asyncBlockResolverDelay) {
      this.asyncBlockResolverDelay = Optional.ofNullable(asyncBlockResolverDelay);
      return this;
    }

    public DasCustodyStand build() {
      checkNotNull(spec);
      final SpecConfigFulu configFulu =
          SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
      if (totalCustodyGroupCount == null) {
        totalCustodyGroupCount = configFulu.getCustodyRequirement();
      }
      if (samplingGroupCount == null) {
        final SpecVersion specVersionFulu = spec.forMilestone(SpecMilestone.FULU);
        samplingGroupCount =
            MiscHelpersFulu.required(specVersionFulu.miscHelpers())
                .getSamplingGroupCount(totalCustodyGroupCount);
      }
      return new DasCustodyStand(
          spec, totalCustodyGroupCount, samplingGroupCount, asyncDbDelay, asyncBlockResolverDelay);
    }
  }

  public static CustodyGroupCountManager createCustodyGroupCountManager(
      final int custodyGroupCount, final int sampleGroupCount) {
    return new CustodyGroupCountManager() {
      @Override
      public int getCustodyGroupCount() {
        return custodyGroupCount;
      }

      @Override
      public Set<UInt64> getCustodyColumnIndices() {
        return IntStream.range(0, custodyGroupCount)
            .mapToObj(UInt64::valueOf)
            .collect(Collectors.toUnmodifiableSet());
      }

      @Override
      public int getSamplingGroupCount() {
        return sampleGroupCount;
      }

      @Override
      public Set<UInt64> getSamplingColumnIndices() {
        return IntStream.range(0, sampleGroupCount)
            .mapToObj(UInt64::valueOf)
            .collect(Collectors.toUnmodifiableSet());
      }
    };
  }
}
