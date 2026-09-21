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

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;

/**
 * Schema for ProgressiveList (EIP-7916) — a variable-length homogeneous collection with no max
 * capacity that uses a progressive merkle tree for stable generalized indices.
 *
 * <p>This is the common case that produces plain {@link SszList} instances. Specialized progressive
 * lists that preserve a {@link SszList} subtype interface extend {@link
 * AbstractSszProgressiveListSchema} directly.
 */
public class SszProgressiveListSchema<ElementDataT extends SszData>
    extends AbstractSszProgressiveListSchema<ElementDataT, SszList<ElementDataT>> {

  public SszProgressiveListSchema(final SszSchema<ElementDataT> elementSchema) {
    super(elementSchema);
  }

  public SszProgressiveListSchema(
      final SszSchema<ElementDataT> elementSchema, final SszSchemaHints hints) {
    super(elementSchema, hints);
  }

  public SszProgressiveListSchema(
      final SszSchema<ElementDataT> elementSchema,
      final SszSchemaHints hints,
      final long maxLength) {
    super(elementSchema, hints, maxLength);
  }

  public static <T extends SszData> SszProgressiveListSchema<T> create(
      final SszSchema<T> elementSchema) {
    return new SszProgressiveListSchema<>(elementSchema);
  }

  public static <T extends SszData> SszProgressiveListSchema<T> create(
      final SszSchema<T> elementSchema, final SszSchemaHints hints) {
    return new SszProgressiveListSchema<>(elementSchema, hints);
  }

  /**
   * A progressive list that additionally enforces {@code maxLength}, both on construction and when
   * deserializing (before any element is materialized).
   */
  public static <T extends SszData> SszProgressiveListSchema<T> create(
      final SszSchema<T> elementSchema, final long maxLength) {
    return new SszProgressiveListSchema<>(elementSchema, SszSchemaHints.none(), maxLength);
  }

  public static <T extends SszData> SszProgressiveListSchema<T> create(
      final SszSchema<T> elementSchema, final SszSchemaHints hints, final long maxLength) {
    return new SszProgressiveListSchema<>(elementSchema, hints, maxLength);
  }
}
