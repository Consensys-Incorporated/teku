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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

public class FailedBlockImportException extends InvalidResponseException {
  private final SignedBeaconBlock block;
  private final BlockImportResult result;

  public FailedBlockImportException(final SignedBeaconBlock block, final BlockImportResult result) {
    super("Unable to import block due to an error " + result.getFailureReason() + ": " + block);
    this.block = block;
    this.result = result;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public BlockImportResult getResult() {
    return result;
  }
}
