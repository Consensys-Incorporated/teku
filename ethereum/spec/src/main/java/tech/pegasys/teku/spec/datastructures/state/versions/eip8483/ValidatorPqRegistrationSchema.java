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

package tech.pegasys.teku.spec.datastructures.state.versions.eip8483;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequestSchema;

public class ValidatorPqRegistrationSchema
    extends ContainerSchema2<ValidatorPqRegistration, SszUInt64, SszByteVector> {

  public ValidatorPqRegistrationSchema() {
    super(
        "ValidatorPQRegistration",
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            "pq_data",
            SszByteVectorSchema.create(ValidatorPqRegistrationRequestSchema.PQ_DATA_SIZE)));
  }

  public ValidatorPqRegistration create(final UInt64 validatorIndex, final Bytes pqData) {
    checkArgument(
        pqData.size() == ValidatorPqRegistrationRequestSchema.PQ_DATA_SIZE,
        "pq_data must be %s bytes",
        ValidatorPqRegistrationRequestSchema.PQ_DATA_SIZE);
    return new ValidatorPqRegistration(this, validatorIndex, pqData);
  }

  @Override
  public ValidatorPqRegistration createFromBackingNode(final TreeNode node) {
    return new ValidatorPqRegistration(this, node);
  }
}
