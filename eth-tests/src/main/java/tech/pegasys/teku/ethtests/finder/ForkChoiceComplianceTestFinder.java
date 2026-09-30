package tech.pegasys.teku.ethtests.finder;

import com.google.errorprone.annotations.MustBeClosed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static tech.pegasys.teku.ethtests.finder.ReferenceTestFinder.unchecked;

@SuppressWarnings("MustBeClosedChecker")
public class ForkChoiceComplianceTestFinder implements TestFinder{

    private final List<String> onlyTestsToRun = new ArrayList<>();
    private final List<String> testsToIgnore = new ArrayList<>();

    /**
     * Allows restricting execution to a specific subset of spec tests. This is especially useful when
     * introducing a new fork and full test coverage is not yet supported.
     *
     * @param onlyTestsToRun Tests that should be executed. Only tests whose identifiers match these
     *     filters—fully or partially—in the format `{fork} - {config} - {test-type} - {test-name}`
     *     will run. If this list is empty, all tests are eligible to run.
     * @param testsToIgnore Tests that should be skipped. Any test whose identifier matches these
     *     filters—fully or partially—in the same format will not run. If this list is empty, no tests
     *     are excluded.
     */
    public ForkChoiceComplianceTestFinder(final List<String> onlyTestsToRun, final List<String> testsToIgnore) {
        this.onlyTestsToRun.addAll(onlyTestsToRun);
        this.testsToIgnore.addAll(testsToIgnore);
    }

    @Override
    @MustBeClosed
    public Stream<TestDefinition> findTests(
            final String fork, final String config, final Path testRoot) throws IOException {
        return Files.list(testRoot.resolve("fork_choice_compliance"))
                .filter(testCategoryPath -> Files.exists(testCategoryPath.resolve(PyspecTestFinder.PYSPEC_TEST_DIRECTORY_NAME)))
                .flatMap(
                        unchecked(
                                testCategoryDir -> findPyspecTestCases(fork, config, testRoot, testCategoryDir)));
    }

    @MustBeClosed
    private Stream<TestDefinition> findPyspecTestCases(
            final String fork, final String config, final Path testRoot, final Path testCategoryDir)
            throws IOException {
        final String testType = "fork_choice_compliance/" + testCategoryDir.getFileName();
        final Path pyspecDir = testCategoryDir.resolve(PyspecTestFinder.PYSPEC_TEST_DIRECTORY_NAME);
        return Files.list(pyspecDir)
                .map(
                        testDir -> {
                            final String testName = pyspecDir.relativize(testDir).toString();
                            return new TestDefinition(
                                    fork, config, testType, testName, testRoot.relativize(testDir).toString());
                        })
                .filter(
                        testDefinition ->
                                onlyTestsToRun.isEmpty()
                                        || onlyTestsToRun.stream()
                                        .anyMatch(
                                                testFilter -> testDefinition.getDisplayName().contains(testFilter)))
                .filter(
                        testDefinition ->
                                testsToIgnore.stream()
                                        .noneMatch(testFilter -> testDefinition.getDisplayName().contains(testFilter)));
    }
}
