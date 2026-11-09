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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class TestServletOutputStream extends ServletOutputStream {
  private final StringBuilder builder = new StringBuilder();
  private int writeCounter = 0;

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void setWriteListener(final WriteListener writeListener) {}

  @Override
  public void write(final int i) {
    writeCounter++;
    builder.append((char) i);
  }

  public String getString() {
    return builder.toString();
  }

  public int getWriteCounter() {
    return writeCounter;
  }

  public int countEvents() {
    return StringUtils.countMatches(getString(), "event: ");
  }

  public int countComments() {
    return (int) Arrays.stream(getString().split("[\r\n]+")).filter(s -> s.startsWith(":")).count();
  }

  public List<String> getEvents() {
    return Arrays.stream(StringUtils.splitByWholeSeparator(getString(), "event: "))
        .filter(s -> !s.startsWith(": ")) // remove SSE comments
        .map(s -> String.format("event: %s", s))
        .collect(Collectors.toList());
  }
}
