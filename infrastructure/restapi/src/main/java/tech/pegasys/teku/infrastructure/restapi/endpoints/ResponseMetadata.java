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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import java.util.Map;
import java.util.Objects;

public class ResponseMetadata {
  private final String contentType;
  private final Map<String, String> additionalHeaders;

  public ResponseMetadata(final String contentType, final Map<String, String> additionalHeaders) {
    this.contentType = contentType;
    this.additionalHeaders = additionalHeaders;
  }

  public String getContentType() {
    return contentType;
  }

  public Map<String, String> getAdditionalHeaders() {
    return additionalHeaders;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ResponseMetadata that = (ResponseMetadata) o;
    return Objects.equals(contentType, that.contentType)
        && Objects.equals(additionalHeaders, that.additionalHeaders);
  }

  @Override
  public int hashCode() {
    return Objects.hash(contentType, additionalHeaders);
  }
}
