package com.camunda.simulator;

import com.camunda.simulator.model.SimulationInput;
import com.camunda.simulator.model.SimulationResult;
import com.camunda.simulator.service.BpmnValidationRunner;
import com.camunda.simulator.service.DMNEvaluator;
import com.camunda.simulator.service.FileManager;
import com.camunda.simulator.service.ProcessEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;

class ProcessEngineTest {
    
    private ProcessEngine processEngine;
    
    @BeforeEach
    void setUp() throws IOException {
        FileManager fileManager = new FileManager();

        // Load DMN file from resources for testing
        InputStream dmnStream = getClass().getClassLoader()
            .getResourceAsStream("static/entry-level-camunda-exercise-v1-0 (1).dmn");
        if (dmnStream != null) {
            byte[] dmnContent = dmnStream.readAllBytes();
            dmnStream.close();
            fileManager.storeDmnFile("entry-level-camunda-exercise-v1-0.dmn", dmnContent);
        }

        // Load BPMN file from resources when available (validates real BPMN; otherwise uses default process)
        InputStream bpmnStream = getClass().getClassLoader()
            .getResourceAsStream("static/entry-level-camunda-exercise-v1-0.bpmn");
        if (bpmnStream != null) {
            byte[] bpmnContent = bpmnStream.readAllBytes();
            bpmnStream.close();
            fileManager.storeBpmnFile("entry-level-camunda-exercise-v1-0.bpmn", bpmnContent);
        }

        DMNEvaluator dmnEvaluator = new DMNEvaluator(fileManager);
        processEngine = new ProcessEngine(fileManager, dmnEvaluator);
    }
    
    @Test
    void testManualPricingInvalid() {
        SimulationInput input = new SimulationInput(true, 30.0);
        SimulationResult result = processEngine.execute(input);

        assertEquals("Invalid", result.getFinalStatus());
        assertEquals("Invalid", result.getDmnResult().getQuoteValidity());
        assertTrue(result.getExecutionPath().stream().anyMatch(s -> s != null && (s.contains("Set Status Invalid") || s.contains("4000"))));
    }
    
    @Test
    void testLowMarginInvalid() {
        SimulationInput input = new SimulationInput(false, 24.0);
        SimulationResult result = processEngine.execute(input);

        assertEquals("Invalid", result.getFinalStatus());
        assertEquals("Invalid", result.getDmnResult().getQuoteValidity());
        assertTrue(result.getExecutionPath().stream().anyMatch(s -> s != null && (s.contains("Set Status Invalid") || s.contains("4000"))));
    }
    
    @Test
    void testValidScenario() {
        SimulationInput input = new SimulationInput(false, 25.0);
        SimulationResult result = processEngine.execute(input);

        assertEquals("Valid", result.getFinalStatus());
        assertEquals("Valid", result.getDmnResult().getQuoteValidity());
        assertTrue(result.getExecutionPath().stream().anyMatch(s -> s != null && (s.contains("Set Status Valid") || s.contains("3000"))));
    }
    
    @Test
    void testExecutionPath() {
        SimulationInput input = new SimulationInput(false, 25.0);
        SimulationResult result = processEngine.execute(input);
        var path = result.getExecutionPath();

        assertTrue(path.contains("Start"));
        assertTrue(path.stream().anyMatch(s -> s != null && (s.contains("Prepare Values") || s.contains("DMN"))));
        assertTrue(path.stream().anyMatch(s -> s != null && (s.contains("Look-up") || s.contains("Results") || s.contains("DMN"))));
        assertTrue(path.stream().anyMatch(s -> s != null && (s.contains("Gateway") || s.contains("Result") || s.contains("Decision"))));
        assertTrue(path.stream().anyMatch(s -> s != null && (s.contains("Set Status") || s.contains("3000") || s.contains("Valid"))));
        assertTrue(path.stream().anyMatch(s -> s != null && (s.equals("End") || s.contains("Terminate") || s.contains("End"))));
    }
    
    @Test
    void testProcessVariables() {
        SimulationInput input = new SimulationInput(false, 25.0);
        SimulationResult result = processEngine.execute(input);

        assertNotNull(result.getProcessVariables());
        assertEquals(25.0, result.getProcessVariables().get("bi_dealMarginPercent"));
        assertEquals(false, result.getProcessVariables().get("bi_manualPriceCost"));
        assertEquals("Valid", result.getProcessVariables().get("quoteValidity"));
        assertEquals("Valid", result.getProcessVariables().get("cim_Status"));
    }

    /**
     * Automation test: runs all test scenarios, compares actual vs expected,
     * and reports "BPMN is valid" when all pass, "BPMN is invalid" otherwise.
     */
    @Test
    void testBpmnValidationAllScenarios() {
        BpmnValidationRunner runner = new BpmnValidationRunner(processEngine);
        BpmnValidationRunner.BpmnValidationResult result = runner.runAll();

        result.printSummary();

        assertTrue(result.isAllPassed(),
            "Expected all scenarios to pass. Failures: " + result.getScenarioResults().stream()
                .filter(r -> !r.isPassed())
                .map(r -> r.getScenarioName() + " (expected=" + r.getExpected() + ", actual=" + r.getActual() + ")")
                .reduce((a, b) -> a + "; " + b).orElse("none"));
        assertEquals("BPMN is valid", result.getValidationMessage());
    }
}

