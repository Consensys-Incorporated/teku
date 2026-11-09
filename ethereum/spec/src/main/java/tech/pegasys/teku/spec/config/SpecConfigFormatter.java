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

package tech.pegasys.teku.spec.config;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;

public class SpecConfigFormatter {
  private static final Converter<String, String> CAMEL_TO_SNAKE_CASE =
      CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.UPPER_UNDERSCORE);

  public static String camelToSnakeCase(final String camelCase) {
    return CAMEL_TO_SNAKE_CASE.convert(camelCase);
  }
}
