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

package tech.pegasys.teku;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.NodeMode;
import tech.pegasys.teku.cli.StartAction;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.io.JemallocDetector;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;

public final class Teku {

  static {
    // Disable libsodium in tuweni Hash because the check for it's presence can be very slow.
    System.setProperty("org.apache.tuweni.crypto.useSodium", "false");
  }

  public static void main(final String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(new TekuDefaultExceptionHandler());

    try {
      Optional<Node> maybeNode = Teku.startFromCLIArgs(args);

      maybeNode.ifPresent(
          node ->
              // Detect SIGTERM
              Runtime.getRuntime()
                  .addShutdownHook(
                      new Thread(
                          () -> {
                            System.out.println("Teku is shutting down");
                            node.stop();
                          })));
    } catch (CLIException e) {
      System.exit(e.getResultCode());
    }
  }

  private static int start(final StartAction startAction, final String... args) {
    final PrintWriter outputWriter = new PrintWriter(System.out, true, Charset.defaultCharset());
    final PrintWriter errorWriter = new PrintWriter(System.err, true, Charset.defaultCharset());
    final LoggingConfigurator loggingConfigurator = new LoggingConfigurator();
    return new BeaconNodeCommand(
            outputWriter, errorWriter, System.getenv(), startAction, loggingConfigurator)
        .parse(args);
  }

  private static Node start(final TekuConfiguration config, final NodeMode nodeMode) {
    final Node node =
        switch (nodeMode) {
          case BOOTNODE_ONLY -> new Bootnode(config);
          case VC_ONLY -> new ValidatorNode(config);
          case COMBINED -> new BeaconNode(config);
          default -> throw new IllegalStateException("Expected node mode to be set");
        };

    // Check that BLS is available before starting to ensure we get a nice error message if it's not
    if (BlstLoader.INSTANCE.isEmpty()) {
      throw new UnsupportedOperationException("BLS native library unavailable for this platform");
    }
    JemallocDetector.logJemallocPresence();

    node.start();

    return node;
  }

  static Optional<Node> startFromCLIArgs(final String[] cliArgs) throws CLIException {
    AtomicReference<Node> nodeRef = new AtomicReference<>();
    int result =
        start((config, validatorClient) -> nodeRef.set(start(config, validatorClient)), cliArgs);
    if (result != 0) {
      throw new CLIException(result);
    }
    return Optional.ofNullable(nodeRef.get());
  }

  static BeaconNode startBeaconNode(final TekuConfiguration config) {
    return (BeaconNode) start(config, NodeMode.COMBINED);
  }

  static Bootnode startBootnode(final TekuConfiguration config) {
    return (Bootnode) start(config, NodeMode.BOOTNODE_ONLY);
  }

  static ValidatorNode startValidatorNode(final TekuConfiguration config) {
    return (ValidatorNode) start(config, NodeMode.VC_ONLY);
  }

  static class CLIException extends RuntimeException {
    private final int resultCode;

    public CLIException(final int resultCode) {
      super("Unable to start Teku. Exit code: " + resultCode);
      this.resultCode = resultCode;
    }

    public int getResultCode() {
      return resultCode;
    }
  }
}
