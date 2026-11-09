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

package tech.pegasys.teku.infrastructure.ssz.sos;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

public class SszField {
  private final int index;
  private final String name;
  private final Supplier<SszSchema<?>> viewType;

  public SszField(final int index, final SszSchema<?> sszSchema) {
    this(index, () -> sszSchema);
  }

  public SszField(final int index, final Supplier<SszSchema<?>> viewType) {
    this(index, "field-" + index, viewType);
  }

  public SszField(final int index, final SszFieldName name, final SszSchema<?> sszSchema) {
    this(index, name.getSszFieldName(), sszSchema);
  }

  public SszField(final int index, final String name, final SszSchema<?> sszSchema) {
    this(index, name, () -> sszSchema);
  }

  public SszField(final int index, final SszFieldName name, final Supplier<SszSchema<?>> viewType) {
    this(index, name.getSszFieldName(), viewType);
  }

  public SszField(final int index, final String name, final Supplier<SszSchema<?>> viewType) {
    this.index = index;
    this.name = name;
    this.viewType = viewType;
  }

  public int getIndex() {
    return index;
  }

  public String getName() {
    return name;
  }

  public Supplier<SszSchema<?>> getSchema() {
    return viewType;
  }
}
