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

package tech.pegasys.teku.spec.datastructures.builder.versions.gloas;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringBasedPrimitiveTypeDefinition.StringTypeBuilder;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteListSchemaImpl;

/** SSZ byte-list schema whose JSON representation is a plain UTF-8 string (format: uri) */
public class UrlSchema extends SszByteListSchemaImpl<SszByteList> {

  private final DeserializableTypeDefinition<SszByteList> jsonTypeDefinition;

  public UrlSchema(final long maxLength) {
    super(SszPrimitiveSchemas.BYTE_SCHEMA, maxLength);
    this.jsonTypeDefinition =
        new StringTypeBuilder<SszByteList>()
            .formatter(v -> new String(v.getBytes().toArray(), StandardCharsets.UTF_8))
            .parser(s -> fromBytes(Bytes.wrap(s.getBytes(StandardCharsets.UTF_8))))
            .format("uri")
            .description("URL")
            .example("https://example.com")
            .build();
  }

  @Override
  public DeserializableTypeDefinition<SszByteList> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }
}
