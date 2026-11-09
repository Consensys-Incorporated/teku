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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.common;

import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public abstract class AbstractSignedBeaconBlockBlinder implements SignedBeaconBlockBlinder {
  protected final SchemaDefinitions schemaDefinitions;

  public AbstractSignedBeaconBlockBlinder(final SchemaDefinitions schemaDefinitions) {
    this.schemaDefinitions = schemaDefinitions;
  }

  @Override
  public SignedBeaconBlock blind(final SignedBeaconBlock signedUnblindedBlock) {
    final SignedBeaconBlock blindedSignedBeaconBlock =
        signedUnblindedBlock.blind(schemaDefinitions);
    checkState(
        blindedSignedBeaconBlock
            .getMessage()
            .hashTreeRoot()
            .equals(signedUnblindedBlock.getMessage().hashTreeRoot()),
        "blinded block root do not match original unblinded block root");

    return blindedSignedBeaconBlock;
  }
}
