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

package tech.pegasys.teku.cli.subcommand;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Scanner;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.util.DatabaseMigrater;
import tech.pegasys.teku.cli.util.DatabaseMigraterError;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;

@CommandLine.Command(
    name = "migrate-database",
    description = "Migrate the database to a specified version.",
    subcommands = {UnstableOptionsCommand.class},
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class MigrateDatabaseCommand implements Runnable {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @CommandLine.Option(
      names = {"--data-base-path", "--data-path"},
      paramLabel = "<string>",
      required = true,
      description = "The path to the teku database.",
      arity = "1")
  private Path dataPath;

  @CommandLine.Option(
      names = {"--data-beacon-path"},
      paramLabel = "<FILENAME>",
      description = "Path to beacon node data\n  Default: <data-base-path>/beacon",
      arity = "1")
  private Path dataBeaconPath;

  @CommandLine.Option(
      names = {"--Xdata-storage-mode"},
      paramLabel = "<STORAGE_MODE>",
      hidden = true,
      description =
          "Sets the strategy for handling historical chain data.  (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private StateStorageMode dataStorageMode = StateStorageMode.ARCHIVE;

  @CommandLine.Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use. (default: mainnet)",
      arity = "1")
  private String network = "mainnet";

  // Use Cases
  // - I have a leveldb2 database and want to update to the latest RocksDB database version
  // - I have updated to the latest db but I've decided i want to go back to an older database
  // format
  //   (eg. testing upgrades!)

  @CommandLine.Option(
      names = {"--Xto"},
      paramLabel = "<format>",
      hidden = true,
      description =
          """
                      The target database version to migrate to.
                      Rocksdb database types supported are 4, 5, 6.
                      Leveldb types supported are leveldb1, leveldb2.""",
      arity = "1")
  private String toDbVersion = DatabaseVersion.V6.getValue();

  // batch size param
  @CommandLine.Option(
      names = {"--Xbatch-size"},
      paramLabel = "<integer>",
      hidden = true,
      description = "MB per transaction (Default: 100)",
      arity = "1")
  private Integer batchSize = 100;

  private DataDirLayout dataDirLayout;

  // OVERVIEW
  // teku folder has a 'beacon' folder
  // If the user wants to change the type of database stored in 'beacon',
  // they can run this command to accomplish that task.
  // While running, a 'beacon.new' folder is created, and we copy data
  // from 'beacon' to 'beacon.new'.
  //
  // If it completes successfully, we move:
  //  - 'beacon' -> 'beacon.old'
  //  - 'beacon.new' -> 'beacon'
  // User is then advised to cleanup 'beacon.old' manually
  //
  // If the process fails to complete, may be left with 'beacon.new',
  // and 'beacon' will have the previous working database in it, so the user
  // could start teku again on the old working database.
  @Override
  public void run() {
    // validate output format
    final Optional<DatabaseVersion> maybeOutputVersion = DatabaseVersion.fromString(toDbVersion);
    if (maybeOutputVersion.isEmpty()) {
      SUB_COMMAND_LOG.exit(2, "Invalid database version specified: " + toDbVersion);
    }
    // validate there is no old database instance present
    // If a previous migrate-data was run, the 'beacon' would have been renamed to 'beacon.old'
    // and advice given to the user to cleanup 'beacon.old' manually.
    if (Files.isDirectory(dataPath.resolve("beacon.old"))) {
      SUB_COMMAND_LOG.exit(
          1,
          "There is an existing folder in "
              + dataPath.resolve("beacon.old").toFile()
              + ", review this folder and remove before continuing.");
    }

    dataDirLayout =
        DataDirLayout.createFrom(
            DataConfig.builder().dataBasePath(dataPath).beaconDataPath(dataBeaconPath).build());

    // validate source database exists
    final DatabaseVersion sourceDatabaseVersion =
        confirmAndCheckOriginalDb(maybeOutputVersion.get());

    final DatabaseMigrater dbMigrater =
        DatabaseMigrater.builder()
            .dataDirLayout(dataDirLayout)
            .network(network)
            .storageMode(dataStorageMode)
            .batchSize(batchSize)
            .statusUpdater(SUB_COMMAND_LOG::display)
            .build();

    try {
      dbMigrater.migrateDatabase(sourceDatabaseVersion, maybeOutputVersion.get());
      SUB_COMMAND_LOG.display("SUCCESS.");
      SUB_COMMAND_LOG.display(
          "The original database is stored in: " + dbMigrater.getMovedOldBeaconFolderPath());
      SUB_COMMAND_LOG.display(
          "This can be removed once you have confirmed the new database works.");
    } catch (DatabaseMigraterError error) {
      SUB_COMMAND_LOG.error("FAILED to migrate database: " + error.getMessage());
      SUB_COMMAND_LOG.display(
          "There is a partially created database at: " + dbMigrater.getNewBeaconFolderPath());
      SUB_COMMAND_LOG.display("This is not in use and could be cleaned up.");
      System.exit(1);
    }
  }

  private DatabaseVersion confirmAndCheckOriginalDb(final DatabaseVersion databaseVersion) {
    // validate source database exists
    final DatabaseVersion sourceDatabaseVersion = validateSourceDatabase(databaseVersion);

    displaySourceDatabaseDetails(sourceDatabaseVersion);
    SUB_COMMAND_LOG.display("Requested database version: " + databaseVersion);
    SUB_COMMAND_LOG.display("A beacon.new folder will be created with the new database");
    SUB_COMMAND_LOG.display("If the data is moved successfully: ");
    SUB_COMMAND_LOG.display(" - The existing beacon folder will become beacon.old");
    SUB_COMMAND_LOG.display(" - The beacon.new folder will become beacon (the active database)");
    SUB_COMMAND_LOG.display(
        " - Once you have confirmed the new database works, you can remove the old database");
    SUB_COMMAND_LOG.display("This operation will need to happen while teku is not running.");
    SUB_COMMAND_LOG.display("");
    if (!confirmYes("Proceed with database migration (yes/no)?")) {
      SUB_COMMAND_LOG.display("Operation cancelled.");
      System.exit(0);
    }
    return sourceDatabaseVersion;
  }

  private boolean confirmYes(final String question) {
    SUB_COMMAND_LOG.display(question);
    final Scanner scanner = new Scanner(System.in, Charset.defaultCharset());
    final String confirmation = scanner.next();
    return confirmation.equalsIgnoreCase("yes");
  }

  private void displaySourceDatabaseDetails(final DatabaseVersion sourceDatabaseVersion) {
    SUB_COMMAND_LOG.display("Current database path: " + dataDirLayout.getBeaconDataDirectory());
    SUB_COMMAND_LOG.display("Current Database Version: " + sourceDatabaseVersion.getValue());
  }

  private DatabaseVersion validateSourceDatabase(final DatabaseVersion databaseVersion) {
    final File currentDatabasePath =
        dataDirLayout.getBeaconDataDirectory().toAbsolutePath().toFile();
    if (!currentDatabasePath.isDirectory()) {
      SUB_COMMAND_LOG.exit(
          1, "Could not locate the existing database to migrate from: " + currentDatabasePath);
    }
    try {
      final String versionValue =
          Files.readString(currentDatabasePath.toPath().resolve("db.version")).trim();
      final DatabaseVersion currentDatabaseVersion =
          DatabaseVersion.fromString(versionValue)
              .orElseThrow(() -> new IOException("Could not read db.version file"));
      if (currentDatabaseVersion.equals(databaseVersion)) {
        SUB_COMMAND_LOG.exit(0, "The specified database is already the requested version");
      }
      return currentDatabaseVersion;
    } catch (IOException e) {
      SUB_COMMAND_LOG.exit(1, "Could not read db.version file");
      // NOT REACHED
      return DatabaseVersion.DEFAULT_VERSION;
    }
  }
}
