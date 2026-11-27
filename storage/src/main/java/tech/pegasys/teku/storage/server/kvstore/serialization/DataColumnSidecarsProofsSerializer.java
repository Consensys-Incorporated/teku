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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.kzg.KZGProof;

/**
 * Serializes the KZG proofs of a slot's extension columns, one inner list per column:
 *
 * <pre>
 * int32 proofsPerColumn                                  (4 bytes, little-endian)
 * column 0: proofsPerColumn x compressed KZG proof       (48 bytes each)
 * column 1: proofsPerColumn x compressed KZG proof
 * ...
 * </pre>
 *
 * <p>Every column holds one proof per blob, so {@code proofsPerColumn} is the block's blob count.
 * It varies per slot and this serializer has no access to the spec to derive it from the total
 * proof count, so it is stored up front to split the flat proofs back into columns.
 */
public class DataColumnSidecarsProofsSerializer implements KvStoreSerializer<List<List<KZGProof>>> {
  @Override
  public List<List<KZGProof>> deserialize(final byte[] data) {
    if (data.length < Integer.BYTES) {
      throw new KvStoreDeserializationException("Missing proofs-per-column header");
    }
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final List<List<KZGProof>> output = new ArrayList<>();
          final List<KZGProof> currentProofs = new ArrayList<>();
          final int proofsPerColumn = reader.readInt32();
          final int proofBytes = data.length - Integer.BYTES;
          if (proofsPerColumn < 0
              || (proofsPerColumn == 0 && proofBytes != 0)
              || (proofsPerColumn > 0
                  && (proofBytes == 0
                      || proofBytes % ((long) proofsPerColumn * KZGProof.SIZE) != 0))) {
            throw new KvStoreDeserializationException(
                String.format(
                    "Invalid archive: %d proof bytes for %d proofs per column",
                    proofBytes, proofsPerColumn));
          }

          while (!reader.isComplete()) {
            final KZGProof kzgProof =
                KZGProof.fromBytesCompressed(Bytes48.wrap(reader.readFixedBytes(KZGProof.SIZE)));
            currentProofs.add(kzgProof);
            if (currentProofs.size() == proofsPerColumn) {
              output.add(new ArrayList<>(currentProofs));
              currentProofs.clear();
            }
          }

          return output;
        });
  }

  @Override
  public byte[] serialize(final List<List<KZGProof>> value) {
    final int proofsPerColumn = value.isEmpty() ? 0 : value.getFirst().size();
    // only the first column's length is stored, so columns of any other length would be regrouped
    // wrongly on read
    if (value.stream().anyMatch(column -> column.size() != proofsPerColumn)) {
      throw new IllegalArgumentException(
          String.format("Expected %d proofs in every column, got ragged columns", proofsPerColumn));
    }
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeInt32(proofsPerColumn);
              value.forEach(
                  column ->
                      column.forEach(
                          kzgProof -> writer.writeFixedBytes(kzgProof.getBytesCompressed())));
            });
    return bytes.toArrayUnsafe();
  }
}
