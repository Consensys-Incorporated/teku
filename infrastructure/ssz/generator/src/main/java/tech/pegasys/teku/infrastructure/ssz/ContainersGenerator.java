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

package tech.pegasys.teku.infrastructure.ssz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContainersGenerator {

  private final int maxFields = 20;
  private final Path templateSrcPath;
  private final Path targetSrcPath;
  private final String typePackagePath = "tech/pegasys/teku/infrastructure/ssz/containers/";
  private final String viewPackagePath = "tech/pegasys/teku/infrastructure/ssz/containers/";
  private final String containerTypeTemplateFile = "ContainerSchemaTemplate.java";
  private final String containerViewTemplateFile = "ContainerTemplate.java";

  public ContainersGenerator(final Path templateSourcePath, final Path destinationSourcePath) {
    templateSrcPath = templateSourcePath;
    targetSrcPath = destinationSourcePath;
  }

  /**
   * Available generation from Gradle with {@code
   * :infrastructure:ssz:generator:generateAndFormatContainers} task
   */
  public static void main(final String[] args) {
    final Path templateSourcePath;
    final Path targetSourcePath;
    if (args.length < 1) {
      templateSourcePath = Paths.get(".", "ssz", "generator", "src", "main", "java");
    } else {
      templateSourcePath = Path.of(args[0]);
    }

    if (args.length < 2) {
      targetSourcePath = Paths.get(".", "ssz", "src", "main", "java");
    } else {
      targetSourcePath = Path.of(args[1]);
    }

    System.out.println(
        "Generating SszContainer classes from templates in: "
            + templateSourcePath.toAbsolutePath()
            + ", to source dir: "
            + targetSourcePath.toAbsolutePath());
    new ContainersGenerator(templateSourcePath, targetSourcePath).generateAll();
    System.out.println("Done.");
  }

  public void generateAll() {
    for (int i = 1; i <= maxFields; i++) {
      generateContainerClasses(i);
    }
  }

  public void generateContainerClasses(final int fieldsCount) {
    String typeClassName = "ContainerSchema" + fieldsCount;
    String viewClassName = "Container" + fieldsCount;
    Map<String, String> vars =
        Map.ofEntries(
            Map.entry("TypeClassName", typeClassName),
            Map.entry("ViewClassName", viewClassName),
            Map.entry(
                "ViewTypes",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "V" + i + " extends SszData")
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "ViewTypeNames",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "V" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "FieldsDeclarations",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "final SszSchema<V" + i + "> fieldSchema" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "NamedFieldsDeclarations",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "final NamedSchema<V" + i + "> fieldNamedSchema" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "Fields",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "fieldSchema" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "NamedFields",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "fieldNamedSchema" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "ViewParams",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "final V" + i + " arg" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "ViewArgs",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "arg" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "Getters",
                IntStream.range(0, fieldsCount)
                    .mapToObj(
                        i ->
                            (""
                                    + "protected V$ getField$() {\n"
                                    + "    return getAny($);\n"
                                    + "  }")
                                .replace("$", "" + i))
                    .collect(Collectors.joining("\n\n"))),
            Map.entry(
                "TypeGetters",
                IntStream.range(0, fieldsCount)
                    .mapToObj(
                        i ->
                            """
                                  @SuppressWarnings("unchecked")
                                  public SszSchema<V$> getFieldSchema$() {
                                    return (SszSchema<V$>) getChildSchema($);
                                  }

                                """
                                .replace("$", "" + i))
                    .collect(Collectors.joining("\n\n"))));
    generateFromTemplate(
        templateSrcPath.resolve(typePackagePath).resolve(containerTypeTemplateFile),
        targetSrcPath.resolve(typePackagePath).resolve(typeClassName + ".java"),
        vars);

    generateFromTemplate(
        templateSrcPath.resolve(viewPackagePath).resolve(containerViewTemplateFile),
        targetSrcPath.resolve(viewPackagePath).resolve(viewClassName + ".java"),
        vars);
  }

  public void generateFromTemplate(
      final Path templateSrc, final Path destSrc, final Map<String, String> varToVal) {
    try {
      String src = Files.readString(templateSrc);
      String res = replacePlaceholders(src, varToVal);
      Files.createDirectories(destSrc.getParent());
      Files.writeString(destSrc, res);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String replacePlaceholders(final String src, final Map<String, String> varToVal) {
    String res = src;
    for (Map.Entry<String, String> entry : varToVal.entrySet()) {
      res =
          res.replaceAll(
              "/\\*\\$\\$" + entry.getKey() + "\\*/[^$]+/\\*\\$\\$\\*/", entry.getValue());
    }
    if (res.contains("/*$$")) {
      throw new RuntimeException("Non substituted placeholders found: " + res);
    }
    return res;
  }
}
