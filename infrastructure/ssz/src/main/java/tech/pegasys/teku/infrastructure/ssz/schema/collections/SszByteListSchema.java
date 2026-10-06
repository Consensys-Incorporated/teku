/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteListSchemaImpl;

public interface SszByteListSchema<SszListT extends SszByteList>
    extends SszPrimitiveListSchema<Byte, SszByte, SszListT> {

  SszListT fromBytes(Bytes bytes);

  static SszByteListSchema<SszByteList> create(final long maxLength) {
    return create(maxLength, SszSchemaHints.none());
  }

  static SszByteListSchema<SszByteList> create(final long maxLength, final SszSchemaHints hints) {
    return new SszByteListSchemaImpl<>(SszPrimitiveSchemas.BYTE_SCHEMA, maxLength, hints);
  }

  static SszByteListSchema<SszByteList> createUInt8(
      final long maxLength, final SszSchemaHints hints) {
    return new SszByteListSchemaImpl<>(SszPrimitiveSchemas.UINT8_SCHEMA, maxLength, hints);
  }
}
