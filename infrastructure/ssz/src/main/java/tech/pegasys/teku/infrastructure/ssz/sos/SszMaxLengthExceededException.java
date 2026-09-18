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

package tech.pegasys.teku.infrastructure.ssz.sos;

/**
 * Deserialization failure raised when a collection is longer than the max length of its schema.
 * Kept as a dedicated type so callers can tell a limit violation apart from malformed SSZ.
 */
public class SszMaxLengthExceededException extends SszDeserializeException {

  public SszMaxLengthExceededException(
      final String collectionKind, final long length, final long maxLength) {
    super(collectionKind + " length " + length + " exceeds max length " + maxLength);
  }
}
