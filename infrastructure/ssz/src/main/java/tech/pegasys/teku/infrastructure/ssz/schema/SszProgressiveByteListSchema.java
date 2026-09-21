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

package tech.pegasys.teku.infrastructure.ssz.schema;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszProgressiveByteListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Progressive (EIP-7916) variant of a byte list. Keeps Teku's {@link SszByteList} interface intact
 * while using progressive merkleization with no fixed max capacity. An optional {@code maxLength}
 * (e.g. {@code MAX_PROOF_SIZE}) is enforced on construction and deserialization; without it the raw
 * SSZ length bounds remain unbounded and network-facing size limits are resolved at gossip/RPC
 * setup.
 */
public class SszProgressiveByteListSchema<SszListT extends SszByteList>
    extends AbstractSszProgressiveListSchema<SszByte, SszListT>
    implements SszByteListSchema<SszListT> {

  private final DeserializableTypeDefinition<SszListT> jsonTypeDefinition;

  public SszProgressiveByteListSchema() {
    this(SszPrimitiveSchemas.BYTE_SCHEMA);
  }

  public SszProgressiveByteListSchema(final long maxLength) {
    this(SszPrimitiveSchemas.BYTE_SCHEMA, SszSchemaHints.none(), maxLength);
  }

  public SszProgressiveByteListSchema(final SszPrimitiveSchema<Byte, SszByte> elementSchema) {
    this(elementSchema, SszSchemaHints.none());
  }

  public SszProgressiveByteListSchema(
      final SszPrimitiveSchema<Byte, SszByte> elementSchema, final SszSchemaHints hints) {
    this(elementSchema, hints, Long.MAX_VALUE);
  }

  public SszProgressiveByteListSchema(
      final SszPrimitiveSchema<Byte, SszByte> elementSchema,
      final SszSchemaHints hints,
      final long maxLength) {
    super(elementSchema, hints, maxLength);
    this.jsonTypeDefinition =
        elementSchema.equals(SszPrimitiveSchemas.BYTE_SCHEMA)
            ? SszPrimitiveTypeDefinitions.sszSerializedType(this, "SSZ encoded byte list")
            : new DeserializableArrayTypeDefinition<>(
                elementSchema.getJsonTypeDefinition(), this::createFromElements);
  }

  @Override
  public DeserializableTypeDefinition<SszListT> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(final TreeNode node) {
    return (SszListT) new SszProgressiveByteListImpl(this, node);
  }

  @Override
  public SszListT fromBytes(final Bytes bytes) {
    try (SszReader reader = SszReader.fromBytes(bytes)) {
      return createFromBackingNode(sszDeserializeTree(reader));
    }
  }

  @Override
  public SszListT createFromElements(final List<? extends SszByte> elements) {
    return fromBytes(
        Bytes.of(elements.stream().mapToInt(sszByte -> 0xFF & sszByte.get()).toArray()));
  }
}
