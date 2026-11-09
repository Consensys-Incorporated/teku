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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.fulu;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class SignedBeaconBlockUnblinderFulu extends AbstractSignedBeaconBlockUnblinder {

  private SafeFuture<Void> completionFuture;

  public SignedBeaconBlockUnblinderFulu(
      final SchemaDefinitionsFulu schemaDefinitions,
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    super(schemaDefinitions, signedBlindedBeaconBlock);
  }

  @Override
  public void setExecutionPayloadSupplier(
      final Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
    throw new RuntimeException("Should not be called for Fulu block");
  }

  @Override
  public void setCompletionSupplier(final Supplier<SafeFuture<Void>> completionFutureSupplier) {
    this.completionFuture = completionFutureSupplier.get();
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> unblind() {

    if (!signedBlindedBeaconBlock.isBlinded()) {
      return SafeFuture.completedFuture(Optional.of(signedBlindedBeaconBlock));
    }

    checkNotNull(completionFuture, "completionFuture must be set");

    return completionFuture.thenApply(__ -> Optional.empty());
  }

  @Override
  public boolean isVersionFulu() {
    return true;
  }
}
