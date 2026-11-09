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

package tech.pegasys.teku.infrastructure.ssz.primitive;

import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszUInt64Schema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszUInt64 extends AbstractSszPrimitive<UInt64> {

  public static final SszUInt64 ZERO = new SszUInt64(UInt64.ZERO);
  public static final SszUInt64 THIRTY_TWO_ETH = new SszUInt64(UInt64.THIRTY_TWO_ETH);
  public static final SszUInt64 MAX_VALUE = new SszUInt64(UInt64.MAX_VALUE);

  public static SszUInt64 of(final UInt64 val) {
    if (val.isZero()) {
      return ZERO;
    }
    if (val.isThirtyTwoEth()) {
      return THIRTY_TWO_ETH;
    }
    if (val.isMaxValue()) {
      return MAX_VALUE;
    }
    return new SszUInt64(val);
  }

  private SszUInt64(final UInt64 val) {
    super(val, SszPrimitiveSchemas.UINT64_SCHEMA);
  }

  protected SszUInt64(final UInt64 val, final AbstractSszUInt64Schema<? extends SszUInt64> schema) {
    super(val, schema);
  }

  public long longValue() {
    return get().longValue();
  }
}
