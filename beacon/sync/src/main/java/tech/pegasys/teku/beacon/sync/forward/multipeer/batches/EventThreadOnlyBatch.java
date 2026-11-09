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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

public class EventThreadOnlyBatch implements Batch {
  private final EventThread eventThread;
  private final Batch delegate;

  public EventThreadOnlyBatch(final EventThread eventThread, final Batch delegate) {
    this.eventThread = eventThread;
    this.delegate = delegate;
  }

  @Override
  public UInt64 getFirstSlot() {
    eventThread.checkOnEventThread();
    return delegate.getFirstSlot();
  }

  @Override
  public UInt64 getLastSlot() {
    eventThread.checkOnEventThread();
    return delegate.getLastSlot();
  }

  @Override
  public UInt64 getCount() {
    eventThread.checkOnEventThread();
    return delegate.getCount();
  }

  @Override
  public Optional<SignedBeaconBlock> getFirstBlock() {
    eventThread.checkOnEventThread();
    return delegate.getFirstBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getLastBlock() {
    eventThread.checkOnEventThread();
    return delegate.getLastBlock();
  }

  @Override
  public List<SignedBeaconBlock> getBlocks() {
    eventThread.checkOnEventThread();
    return delegate.getBlocks();
  }

  @Override
  public Map<Bytes32, List<BlobSidecar>> getBlobSidecarsByBlockRoot() {
    eventThread.checkOnEventThread();
    return delegate.getBlobSidecarsByBlockRoot();
  }

  @Override
  public Map<Bytes32, SignedExecutionPayloadEnvelope> getExecutionPayloadsByBlockRoot() {
    eventThread.checkOnEventThread();
    return delegate.getExecutionPayloadsByBlockRoot();
  }

  @Override
  public void dropExecutionPayload(final Bytes32 blockRoot) {
    eventThread.checkOnEventThread();
    delegate.dropExecutionPayload(blockRoot);
  }

  @Override
  public boolean restoreExecutionPayload(final Bytes32 blockRoot) {
    eventThread.checkOnEventThread();
    return delegate.restoreExecutionPayload(blockRoot);
  }

  @Override
  public Optional<SyncSource> getSource() {
    eventThread.checkOnEventThread();
    return delegate.getSource();
  }

  @Override
  public void markComplete() {
    eventThread.checkOnEventThread();
    delegate.markComplete();
  }

  @Override
  public boolean isComplete() {
    eventThread.checkOnEventThread();
    return delegate.isComplete();
  }

  @Override
  public boolean isConfirmed() {
    eventThread.checkOnEventThread();
    return delegate.isConfirmed();
  }

  @Override
  public boolean isFirstBlockConfirmed() {
    eventThread.checkOnEventThread();
    return delegate.isFirstBlockConfirmed();
  }

  @Override
  public boolean isContested() {
    eventThread.checkOnEventThread();
    return delegate.isContested();
  }

  @Override
  public void markFirstBlockConfirmed() {
    eventThread.checkOnEventThread();
    delegate.markFirstBlockConfirmed();
  }

  @Override
  public void markLastBlockConfirmed() {
    eventThread.checkOnEventThread();
    delegate.markLastBlockConfirmed();
  }

  @Override
  public void markAsContested() {
    eventThread.checkOnEventThread();
    delegate.markAsContested();
  }

  @Override
  public boolean isEmpty() {
    eventThread.checkOnEventThread();
    return delegate.isEmpty();
  }

  @Override
  public boolean isAwaitingBlocks() {
    eventThread.checkOnEventThread();
    return delegate.isAwaitingBlocks();
  }

  @Override
  public void requestMoreBlocks(final Runnable callback) {
    eventThread.checkOnEventThread();
    delegate.requestMoreBlocks(callback);
  }

  @Override
  public TargetChain getTargetChain() {
    eventThread.checkOnEventThread();
    return delegate.getTargetChain();
  }

  @Override
  public void markAsInvalid() {
    eventThread.checkOnEventThread();
    delegate.markAsInvalid();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("delegate", delegate).toString();
  }
}
