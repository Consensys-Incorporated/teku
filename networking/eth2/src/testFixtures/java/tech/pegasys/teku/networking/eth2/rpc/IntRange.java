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

package tech.pegasys.teku.networking.eth2.rpc;

public class IntRange {
  public static IntRange ofLength(final int startIndex, final int length) {
    return new IntRange(startIndex, length);
  }

  public static IntRange ofIndices(final int startIndex, final int endIndex) {
    return new IntRange(startIndex, endIndex - startIndex);
  }

  private final int startIndex;
  private final int length;

  public IntRange(final int startIndex, final int length) {
    this.startIndex = startIndex;
    this.length = length;
  }

  public int getStartIndex() {
    return startIndex;
  }

  public int getEndIndex() {
    return startIndex + length;
  }

  public int getLength() {
    return length;
  }
}
