/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.ethtests;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.commons.text.CaseUtils;
import org.apache.commons.text.StringEscapeUtils;
import tech.pegasys.teku.ethtests.finder.ReferenceTestFinder;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

public class ReferenceTestGenerator {

  public static final Charset CHARSET = StandardCharsets.UTF_8;

  public static void main(final String[] args) throws IOException {
    final Path outputDir = Path.of(args[0]);
    try (final Stream<TestDefinition> tests = ReferenceTestFinder.findReferenceTests()) {
      tests.forEach(testDefinition -> generateReferenceTest(outputDir, testDefinition));
    }
  }

  private static void generateReferenceTest(
      final Path outputDir, final TestDefinition testDefinition) {
    try {
      final String template =
          Resources.toString(Resources.getResource("ReferenceTestTemplate.java"), CHARSET);
      final String testClassName = getTestClassName(testDefinition);
      final String testPackage =
          "tech.pegasys.teku.reference."
              + (testDefinition.getFork().isEmpty()
                  ? ""
                  : sanitizePackageName(testDefinition.getFork()) + ".")
              + sanitizePackageName(testDefinition.getTestType());
      final String testMethodName = "test" + toCamelCase(testDefinition.getTestName());
      final String content =
          template
              .replace("$TEST_PACKAGE$", testPackage)
              .replace("$TEST_CLASS_NAME$", testClassName)
              .replace("$TEST_METHOD_NAME$", testMethodName)
              .replace("$CONFIG$", testDefinition.getConfigName())
              .replace("$FORK$", testDefinition.getFork())
              .replace("$TEST_TYPE$", testDefinition.getTestType())
              .replace("$TEST_NAME$", testDefinition.getTestName())
              .replace(
                  "$RELATIVE_PATH$",
                  StringEscapeUtils.escapeJava(testDefinition.getPathFromPhaseTestDir()));

      final String relativePath =
          testPackage.replace('.', File.separatorChar)
              + File.separatorChar
              + testClassName
              + ".java";
      final Path testFile = outputDir.resolve(relativePath);
      final File targetDirectory = testFile.getParent().toFile();
      if (!targetDirectory.mkdirs() && !targetDirectory.isDirectory()) {
        throw new IOException("Failed to create directory " + targetDirectory);
      }
      Files.writeString(testFile, content, CHARSET);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String getTestClassName(final TestDefinition testDefinition) {
    return toCamelCase(testDefinition.getConfigName())
        + toCamelCase(testDefinition.getTestType())
        + toCamelCase(testDefinition.getTestName())
        + "ReferenceTest";
  }

  private static String toCamelCase(final String input) {
    if (input.isEmpty()) {
      return input;
    }
    return CaseUtils.toCamelCase(sanitise(input), true, '_');
  }

  private static String sanitise(final String input) {
    return input.replaceAll("[^a-zA-Z0-9]", "_");
  }

  private static String sanitizePackageName(final String packageName) {
    return sanitise(packageName.toLowerCase(Locale.US));
  }
}
