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

package tech.pegasys.teku.validator.api;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class PublishSignedExecutionPayloadResult {

  private final Bytes32 beaconBlockRoot;
  private final Optional<String> rejectionReason;
  private final boolean published;

  private PublishSignedExecutionPayloadResult(
      final Bytes32 beaconBlockRoot,
      final Optional<String> rejectionReason,
      final boolean published) {
    this.beaconBlockRoot = beaconBlockRoot;
    this.rejectionReason = rejectionReason;
    this.published = published;
  }

  public static PublishSignedExecutionPayloadResult success(final Bytes32 beaconBlockRoot) {
    return new PublishSignedExecutionPayloadResult(beaconBlockRoot, Optional.empty(), true);
  }

  public static PublishSignedExecutionPayloadResult notImported(
      final Bytes32 beaconBlockRoot, final String reason) {
    return new PublishSignedExecutionPayloadResult(beaconBlockRoot, Optional.of(reason), true);
  }

  public static PublishSignedExecutionPayloadResult rejected(
      final Bytes32 beaconBlockRoot, final String reason) {
    return new PublishSignedExecutionPayloadResult(beaconBlockRoot, Optional.of(reason), false);
  }

  public Bytes32 getBeaconBlockRoot() {
    return beaconBlockRoot;
  }

  public Optional<String> getRejectionReason() {
    return rejectionReason;
  }

  public boolean isPublished() {
    return published;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PublishSignedExecutionPayloadResult that = (PublishSignedExecutionPayloadResult) o;
    return Objects.equals(beaconBlockRoot, that.beaconBlockRoot)
        && Objects.equals(rejectionReason, that.rejectionReason)
        && published == that.published;
  }

  @Override
  public int hashCode() {
    return Objects.hash(beaconBlockRoot, rejectionReason, published);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("beaconBlockRoot", beaconBlockRoot)
        .add("rejectionReason", rejectionReason)
        .add("published", published)
        .toString();
  }
}
