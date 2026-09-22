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

package tech.pegasys.teku.cli.subcommand.debug;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.errorprone.annotations.MustBeClosed;
import io.airlift.compress.v3.snappy.SnappyDecompressor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.apache.commons.io.IOUtils;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistryBuilder;

@Command(
    name = "pretty-print",
    aliases = {"pp"},
    description = "Print SSZ objects as formatted JSON",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class PrettyPrintCommand implements Callable<Integer> {

  @Option(
      names = {"-f", "--format"},
      paramLabel = "<FORMAT>",
      description = "Output format to use",
      arity = "1")
  @SuppressWarnings("FieldCanBeFinal")
  private OutputFormat format = OutputFormat.JSON;

  @Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Network config to use when decoding objects.",
      arity = "1")
  @SuppressWarnings("FieldCanBeFinal")
  private String network = "mainnet";

  @Parameters(
      index = "0",
      paramLabel = "<MILESTONE>",
      description = "Milestone to use when decoding objects.",
      arity = "1")
  @SuppressWarnings("FieldCanBeFinal")
  private SpecMilestone milestone = SpecMilestone.ALTAIR;

  @Parameters(
      index = "1",
      paramLabel = "<TYPE>",
      description = "Type of object to print",
      arity = "1")
  private SszObjectType type;

  @Parameters(
      index = "2",
      paramLabel = "<FILENAME>",
      description = "Path/filename of the ssz file to read (default: read from stdin)",
      arity = "0..1")
  private File input;

  @Override
  public Integer call() throws IOException {
    final SpecVersion spec =
        SpecVersion.create(
                milestone,
                SpecConfigLoader.loadConfig(network).specConfig(),
                SchemaRegistryBuilder.create())
            .orElseThrow();
    final Bytes inputData;
    try (final InputStream in = openStream()) {
      inputData = Bytes.wrap(IOUtils.toByteArray(in));
    }
    final SszSchema<?> schema = type.getSchema(spec);
    final String json = prettyPrint(schema, inputData);
    SubCommandLogger.SUB_COMMAND_LOG.display(json);
    return 0;
  }

  private <T extends SszData> String prettyPrint(final SszSchema<T> schema, final Bytes ssz)
      throws JsonProcessingException {
    final T value = schema.sszDeserialize(ssz);
    return JsonUtil.serialize(
        format.createFactory(),
        gen -> {
          gen.useDefaultPrettyPrinter();
          schema.getJsonTypeDefinition().serialize(value, gen);
        });
  }

  @MustBeClosed
  private InputStream openStream() throws IOException {
    if (input == null) {
      return System.in;
    } else if (input.getName().endsWith(".ssz_snappy")) {
      final byte[] data = IOUtils.toByteArray(Files.newInputStream(input.toPath()));
      final SnappyDecompressor decompressor = SnappyDecompressor.create();
      final int uncompressedLength = decompressor.getUncompressedLength(data, 0);
      final byte[] uncompressed = new byte[uncompressedLength];
      decompressor.decompress(data, 0, data.length, uncompressed, 0, uncompressedLength);
      return new ByteArrayInputStream(uncompressed);
    } else if (input.getName().endsWith(".ssz")) {
      final byte[] data = IOUtils.toByteArray(Files.newInputStream(input.toPath()));
      return new ByteArrayInputStream(data);
    } else if (input.getName().endsWith(".hex")) {
      final String str = Files.readString(input.toPath()).trim();
      final byte[] data = Bytes.fromHexString(str).toArrayUnsafe();
      return new ByteArrayInputStream(data);
    } else {
      return Files.newInputStream(input.toPath());
    }
  }

  public enum OutputFormat {
    JSON(JsonFactory::new),
    YAML(YAMLFactory::new);

    private final Supplier<? extends JsonFactory> createFactory;

    OutputFormat(final Supplier<? extends JsonFactory> createFactory) {
      this.createFactory = createFactory;
    }

    public JsonFactory createFactory() {
      return createFactory.get();
    }
  }
}
