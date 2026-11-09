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

package tech.pegasys.teku.spec.datastructures.execution;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class NewPayloadRequest {

  private final ExecutionPayload executionPayload;
  private final Optional<List<VersionedHash>> versionedHashes;
  private final Optional<Bytes32> parentBeaconBlockRoot;
  private final Optional<List<Bytes>> executionRequests;

  public NewPayloadRequest(final ExecutionPayload executionPayload) {
    this.executionPayload = executionPayload;
    this.versionedHashes = Optional.empty();
    this.parentBeaconBlockRoot = Optional.empty();
    this.executionRequests = Optional.empty();
  }

  public NewPayloadRequest(
      final ExecutionPayload executionPayload,
      final List<VersionedHash> versionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    this.executionPayload = executionPayload;
    this.versionedHashes = Optional.of(versionedHashes);
    this.parentBeaconBlockRoot = Optional.of(parentBeaconBlockRoot);
    this.executionRequests = Optional.empty();
  }

  public NewPayloadRequest(
      final ExecutionPayload executionPayload,
      final List<VersionedHash> versionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests) {
    this.executionPayload = executionPayload;
    this.versionedHashes = Optional.of(versionedHashes);
    this.parentBeaconBlockRoot = Optional.of(parentBeaconBlockRoot);
    this.executionRequests = Optional.of(executionRequests);
  }

  public ExecutionPayload getExecutionPayload() {
    return executionPayload;
  }

  public Optional<List<VersionedHash>> getVersionedHashes() {
    return versionedHashes;
  }

  public Optional<Bytes32> getParentBeaconBlockRoot() {
    return parentBeaconBlockRoot;
  }

  public Optional<List<Bytes>> getExecutionRequests() {
    return executionRequests;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof final NewPayloadRequest that) {

      return Objects.equals(executionPayload, that.executionPayload)
          && Objects.equals(versionedHashes, that.versionedHashes)
          && Objects.equals(parentBeaconBlockRoot, that.parentBeaconBlockRoot)
          && Objects.equals(executionRequests, that.executionRequests);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        executionPayload, versionedHashes, parentBeaconBlockRoot, executionRequests);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayload", executionPayload)
        .add("versionedHashes", versionedHashes)
        .add("parentBeaconBlockRoot", parentBeaconBlockRoot)
        .add("executionRequests", executionRequests)
        .toString();
  }
}
