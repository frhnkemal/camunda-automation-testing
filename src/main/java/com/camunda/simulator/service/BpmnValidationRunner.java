package com.camunda.simulator.service;

import com.camunda.simulator.model.SimulationInput;
import com.camunda.simulator.model.SimulationResult;
import com.camunda.simulator.model.TestScenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Runs all BPMN test scenarios, compares actual results with expectations,
 * and reports whether the BPMN is valid or invalid.
 */
public class BpmnValidationRunner {

    private final ProcessEngine processEngine;

    /** Expected execution path key steps: Invalid branch (order matters; each step matched by substring). */
    private static final List<String> EXPECTED_PATH_INVALID = List.of("Start", "Prepare", "Look-up", "Gateway", "Invalid", "End");
    /** Expected execution path key steps: Valid branch. */
    private static final List<String> EXPECTED_PATH_VALID = List.of("Start", "Prepare", "Look-up", "Gateway", "Valid", "End");

    /** All test scenarios used for validation (aligned with API /api/scenarios). Includes edge cases. */
    private static final List<TestScenario> TEST_SCENARIOS = List.of(
        // Manual pricing: always Invalid
        new TestScenario(
            "Manual Pricing - Invalid",
            "Manual pricing always results in Invalid status",
            new SimulationInput(true, 30.0),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        new TestScenario(
            "Manual with Zero Margin",
            "Manual pricing with 0% margin - still Invalid",
            new SimulationInput(true, 0.0),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        new TestScenario(
            "Manual with High Margin",
            "Manual pricing with 99% margin - still Invalid (manual overrides margin)",
            new SimulationInput(true, 99.0),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        // Below 25% margin: Invalid
        new TestScenario(
            "Low Margin - Invalid",
            "Margin below 25% results in Invalid status",
            new SimulationInput(false, 24.0),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        new TestScenario(
            "Zero Margin",
            "0% margin without manual pricing - Invalid",
            new SimulationInput(false, 0.0),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        new TestScenario(
            "One Percent Margin",
            "1% margin - Invalid",
            new SimulationInput(false, 1.0),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        new TestScenario(
            "23% Margin",
            "23% margin - below threshold, Invalid",
            new SimulationInput(false, 23.0),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        new TestScenario(
            "Just Below 25% - 24.99",
            "Edge: 24.99% margin (< 25) - Invalid",
            new SimulationInput(false, 24.99),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        new TestScenario(
            "Just Below 25% - 24.9",
            "Edge: 24.9% margin - Invalid",
            new SimulationInput(false, 24.9),
            "Invalid",
            EXPECTED_PATH_INVALID
        ),
        // Boundary and above 25%: Valid
        new TestScenario(
            "Valid - Exactly 25%",
            "Boundary: exactly 25% margin - Valid",
            new SimulationInput(false, 25.0),
            "Valid",
            EXPECTED_PATH_VALID
        ),
        new TestScenario(
            "Just Above 25% - 25.01",
            "Edge: 25.01% margin (>= 25) - Valid",
            new SimulationInput(false, 25.01),
            "Valid",
            EXPECTED_PATH_VALID
        ),
        new TestScenario(
            "Just Above 25% - 25.1",
            "Edge: 25.1% margin - Valid",
            new SimulationInput(false, 25.1),
            "Valid",
            EXPECTED_PATH_VALID
        ),
        new TestScenario(
            "26% Margin",
            "26% margin - Valid",
            new SimulationInput(false, 26.0),
            "Valid",
            EXPECTED_PATH_VALID
        ),
        new TestScenario(
            "High Margin - 30%",
            "30% margin with no manual pricing - Valid",
            new SimulationInput(false, 30.0),
            "Valid",
            EXPECTED_PATH_VALID
        ),
        new TestScenario(
            "Very High Margin - 100%",
            "Edge: 100% margin - Valid",
            new SimulationInput(false, 100.0),
            "Valid",
            EXPECTED_PATH_VALID
        )
    );

    /** Input validation test cases: invalid JSON payload and expected error message substring. */
    private static final List<InputValidationCase> INPUT_VALIDATION_SCENARIOS = List.of(
        new InputValidationCase(
            "Reject dealMarginPercent as string",
            "dealMarginPercent must be a number, not text",
            "{\"manualPriceCost\": false, \"dealMarginPercent\": \"abc\"}",
            "number"
        ),
        new InputValidationCase(
            "Reject dealMarginPercent as boolean",
            "dealMarginPercent must be a number",
            "{\"manualPriceCost\": false, \"dealMarginPercent\": true}",
            "number"
        ),
        new InputValidationCase(
            "Reject manualPriceCost as string",
            "manualPriceCost must be a boolean",
            "{\"manualPriceCost\": \"yes\", \"dealMarginPercent\": 25}",
            "boolean"
        ),
        new InputValidationCase(
            "Reject manualPriceCost as number",
            "manualPriceCost must be a boolean",
            "{\"manualPriceCost\": 1, \"dealMarginPercent\": 25}",
            "boolean"
        ),
        new InputValidationCase(
            "Reject missing manualPriceCost",
            "manualPriceCost is required",
            "{\"dealMarginPercent\": 25}",
            "manualPriceCost"
        ),
        new InputValidationCase(
            "Reject missing dealMarginPercent",
            "dealMarginPercent is required",
            "{\"manualPriceCost\": false}",
            "dealMarginPercent"
        )
    );

    /** Input validation case: invalid JSON and expected error message substring. */
    public static final class InputValidationCase {
        private final String name;
        private final String description;
        private final String invalidJson;
        private final String expectedErrorSubstring;

        public InputValidationCase(String name, String description, String invalidJson, String expectedErrorSubstring) {
            this.name = name;
            this.description = description;
            this.invalidJson = invalidJson;
            this.expectedErrorSubstring = expectedErrorSubstring;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getInvalidJson() { return invalidJson; }
        public String getExpectedErrorSubstring() { return expectedErrorSubstring; }
    }

    public BpmnValidationRunner(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    /**
     * Returns the list of test scenarios used for validation.
     */
    public static List<TestScenario> getTestScenarios() {
        return Collections.unmodifiableList(TEST_SCENARIOS);
    }

    /**
     * Returns the list of input validation scenarios (invalid payloads that must be rejected).
     */
    public static List<InputValidationCase> getInputValidationScenarios() {
        return Collections.unmodifiableList(INPUT_VALIDATION_SCENARIOS);
    }

    /**
     * Runs all test scenarios, compares actual final status with expected result,
     * and returns a validation result.
     */
    /**
     * Checks if actual execution path contains expected key steps in order.
     * Each expected step is matched by substring. Handles BPMN vs default naming:
     * - "Gateway" matches "Gateway", "Result", or "Test Result"
     * - "Invalid" matches "Invalid" or "4000"
     * - "Valid" matches "Valid" or "3000"
     * - "End" matches "End" or "Terminate"
     * - "Look-up" matches "Look-up Result" or "Look-up Results"
     */
    private static boolean executionPathMatches(List<String> actualPath, List<String> expectedSteps) {
        if (actualPath == null || expectedSteps == null || expectedSteps.isEmpty()) {
            return true;
        }
        int actualIndex = 0;
        for (String expectedStep : expectedSteps) {
            String expectedLower = expectedStep.toLowerCase(Locale.ROOT);
            boolean found = false;
            while (actualIndex < actualPath.size()) {
                String actualStep = actualPath.get(actualIndex);
                if (actualStep == null) {
                    actualIndex++;
                    continue;
                }
                String actualLower = actualStep.toLowerCase(Locale.ROOT);
                boolean stepMatches = actualLower.contains(expectedLower)
                    || ("end".equals(expectedLower) && (actualLower.contains("terminate") || actualLower.contains("end")))
                    || ("gateway".equals(expectedLower) && (actualLower.contains("gateway") || actualLower.contains("result") || actualLower.contains("decision")))
                    || ("invalid".equals(expectedLower) && (actualLower.contains("invalid") || actualLower.contains("4000")))
                    || ("valid".equals(expectedLower) && (actualLower.contains("valid") || actualLower.contains("3000")));
                if (stepMatches) {
                    found = true;
                    actualIndex++;
                    break;
                }
                actualIndex++;
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public BpmnValidationResult runAll() {
        List<ScenarioResult> results = new ArrayList<>();
        for (TestScenario scenario : TEST_SCENARIOS) {
            SimulationResult simulationResult = processEngine.execute(scenario.getInputs());
            String actualStatus = simulationResult.getFinalStatus();
            String expectedStatus = scenario.getExpectedResult();
            boolean statusPassed = expectedStatus != null && expectedStatus.equals(actualStatus);

            List<String> actualPath = simulationResult.getExecutionPath();
            List<String> expectedPath = scenario.getExpectedExecutionPath();
            boolean pathPassed = executionPathMatches(actualPath, expectedPath);

            String expectedPathStr = expectedPath != null ? String.join(" → ", expectedPath) : null;
            String actualPathStr = actualPath != null ? String.join(" → ", actualPath) : null;

            boolean passed = statusPassed && pathPassed;
            results.add(new ScenarioResult(
                scenario.getName(),
                scenario.getDescription(),
                expectedStatus,
                actualStatus,
                passed,
                pathPassed,
                expectedPathStr,
                actualPathStr
            ));
        }
        for (InputValidationCase v : INPUT_VALIDATION_SCENARIOS) {
            List<String> errors = InputValidator.validate(v.invalidJson);
            boolean rejected = !errors.isEmpty();
            String firstError = rejected ? errors.get(0) : "";
            boolean passed = rejected && firstError.toLowerCase().contains(v.expectedErrorSubstring.toLowerCase());
            String actual = rejected ? ("Rejected: " + firstError) : "Accepted (expected rejection)";
            results.add(new ScenarioResult(
                v.name,
                v.description,
                "Rejected",
                actual,
                passed,
                true,
                null,
                null
            ));
        }
        boolean allPassed = results.stream().allMatch(ScenarioResult::isPassed);
        return new BpmnValidationResult(allPassed, results);
    }

    /**
     * Result of running a single test scenario.
     */
    public static class ScenarioResult {
        private final String scenarioName;
        private final String description;
        private final String expected;
        private final String actual;
        private final boolean passed;
        private final Boolean pathPassed;
        private final String expectedPath;
        private final String actualPath;

        public ScenarioResult(String scenarioName, String description, String expected, String actual, boolean passed) {
            this(scenarioName, description, expected, actual, passed, null, null, null);
        }

        public ScenarioResult(String scenarioName, String description, String expected, String actual, boolean passed,
                             Boolean pathPassed, String expectedPath, String actualPath) {
            this.scenarioName = scenarioName;
            this.description = description;
            this.expected = expected;
            this.actual = actual;
            this.passed = passed;
            this.pathPassed = pathPassed;
            this.expectedPath = expectedPath;
            this.actualPath = actualPath;
        }

        public String getScenarioName() { return scenarioName; }
        public String getDescription() { return description; }
        public String getExpected() { return expected; }
        public String getActual() { return actual; }
        public boolean isPassed() { return passed; }
        public Boolean getPathPassed() { return pathPassed; }
        public String getExpectedPath() { return expectedPath; }
        public String getActualPath() { return actualPath; }
    }

    /**
     * Overall BPMN validation result: all scenarios passed or not, plus per-scenario details.
     */
    public static class BpmnValidationResult {
        private final boolean allPassed;
        private final List<ScenarioResult> scenarioResults;

        public BpmnValidationResult(boolean allPassed, List<ScenarioResult> scenarioResults) {
            this.allPassed = allPassed;
            this.scenarioResults = Collections.unmodifiableList(new ArrayList<>(scenarioResults));
        }

        public boolean isAllPassed() {
            return allPassed;
        }

        public List<ScenarioResult> getScenarioResults() {
            return scenarioResults;
        }

        /**
         * Returns "BPMN is valid" when all test scenarios passed, "BPMN is invalid" otherwise.
         */
        public String getValidationMessage() {
            return allPassed ? "BPMN is valid" : "BPMN is invalid";
        }

        /**
         * Prints a summary of each scenario and the final validation message to System.out.
         */
        public void printSummary() {
            System.out.println();
            System.out.println("========== BPMN Validation Results ==========");
            for (ScenarioResult r : scenarioResults) {
                String status = r.isPassed() ? "PASS" : "FAIL";
                System.out.printf("  [%s] %s: expected=%s, actual=%s%n",
                    status, r.getScenarioName(), r.getExpected(), r.getActual());
                if (r.getPathPassed() != null && r.getExpectedPath() != null) {
                    System.out.printf("       Path: %s | actual: %s%n", r.getPathPassed() ? "PASS" : "FAIL", r.getActualPath());
                }
            }
            System.out.println("==============================================");
            System.out.println("  Result: " + getValidationMessage());
            System.out.println("==============================================");
            System.out.println();
        }
    }
}
