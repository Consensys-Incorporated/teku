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

package tech.pegasys.teku.spec.datastructures.blocks;

import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;

/**
 * Interface used to represent both {@link SignedBeaconBlockSchema} and all
 * SignedBlockContentsSchema[Milestone] and their blinded variants
 */
public interface SignedBlockContainerSchema<T extends SignedBlockContainer>
    extends SszContainerSchema<T> {

  @SuppressWarnings("unchecked")
  default SignedBlockContainerSchema<SignedBlockContainer> castTypeToSignedBlockContainer() {
    return (SignedBlockContainerSchema<SignedBlockContainer>) this;
  }
}
