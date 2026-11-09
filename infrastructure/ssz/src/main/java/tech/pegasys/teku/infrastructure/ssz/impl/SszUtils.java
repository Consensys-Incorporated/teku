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

package tech.pegasys.teku.infrastructure.ssz.impl;

import com.google.common.collect.Streams;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableVector;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;

/** Handy view tool methods */
public class SszUtils {

  public static <C, V extends SszData> SszList<V> toSszList(
      final SszSchema<? extends SszList<V>> type,
      final Iterable<C> list,
      final Function<C, V> converter) {
    return toSszList(type, Streams.stream(list).map(converter).toList());
  }

  public static <V extends SszData> SszList<V> toSszList(
      final SszSchema<? extends SszList<V>> type, final Iterable<V> list) {
    SszMutableList<V> ret = type.getDefault().createWritableCopy();
    list.forEach(ret::append);
    return ret.commitChanges();
  }

  public static <C, V extends SszData> SszVector<V> toSszVector(
      final SszVectorSchema<V, ?> type, final Iterable<C> list, final Function<C, V> converter) {
    return toSszVector(type, Streams.stream(list).map(converter).toList());
  }

  public static <V extends SszData> SszVector<V> toSszVector(
      final SszVectorSchema<V, ?> type, final Iterable<V> list) {
    SszMutableVector<V> ret = type.getDefault().createWritableCopy();
    int idx = 0;
    for (V v : list) {
      if (idx >= type.getLength()) {
        throw new IllegalArgumentException("List size exceeds vector size");
      }
      ret.set(idx, v);
      idx++;
    }
    return ret.commitChanges();
  }

  public static SszList<SszByte> toSszByteList(
      final SszListSchema<SszByte, ?> type, final Bytes bytes) {
    return type.sszDeserialize(SszReader.fromBytes(bytes));
  }

  /** Retrieve bytes from vector of bytes to a {@link Bytes} instance */
  public static Bytes getAllBytes(final SszCollection<SszByte> vector) {
    return vector.sszSerialize();
  }
}
