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

package tech.pegasys.teku.kzg;

import static com.google.common.base.Preconditions.checkArgument;
import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_CELL;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;

public record KZGCell(Bytes bytes) {

  static final KZGCell ZERO = new KZGCell(Bytes.wrap(new byte[BYTES_PER_CELL]));

  public KZGCell(final Bytes bytes) {
    checkArgument(
        bytes.size() == BYTES_PER_CELL,
        "Expected " + BYTES_PER_CELL + " bytes but received %s.",
        bytes.size());
    this.bytes = bytes;
  }

  static List<KZGCell> splitBytes(final Bytes bytes) {
    return CKZG4844Utils.chunkBytes(bytes, BYTES_PER_CELL).stream().map(KZGCell::new).toList();
  }
}
