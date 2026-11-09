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

package tech.pegasys.teku.cli.converter;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine;

public class MetricCategoryConverter implements CommandLine.ITypeConverter<MetricCategory> {

  private final Map<String, MetricCategory> metricCategories = new HashMap<>();

  @Override
  public MetricCategory convert(final String value) {
    checkNotNull(value, "Value to convert must not be null");
    final MetricCategory category = metricCategories.get(value.toUpperCase(Locale.ROOT));
    if (category == null) {
      throw new IllegalArgumentException("Unknown category: " + value);
    }
    return category;
  }

  public <T extends Enum<T> & MetricCategory> void addCategories(final Class<T> categoryEnum) {
    EnumSet.allOf(categoryEnum)
        .forEach(
            category -> metricCategories.put(category.name().toUpperCase(Locale.ROOT), category));
  }
}
