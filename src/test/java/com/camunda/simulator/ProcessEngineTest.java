package com.camunda.simulator;

import com.camunda.simulator.model.SimulationInput;
import com.camunda.simulator.model.SimulationResult;
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
        
        DMNEvaluator dmnEvaluator = new DMNEvaluator(fileManager);
        processEngine = new ProcessEngine(fileManager, dmnEvaluator);
    }
    
    @Test
    void testManualPricingInvalid() {
        SimulationInput input = new SimulationInput(true, 30.0);
        SimulationResult result = processEngine.execute(input);
        
        assertEquals("Invalid", result.getFinalStatus());
        assertEquals("Invalid", result.getDmnResult().getQuoteValidity());
        assertTrue(result.getExecutionPath().contains("Set Status Invalid"));
    }
    
    @Test
    void testLowMarginInvalid() {
        SimulationInput input = new SimulationInput(false, 24.0);
        SimulationResult result = processEngine.execute(input);
        
        assertEquals("Invalid", result.getFinalStatus());
        assertEquals("Invalid", result.getDmnResult().getQuoteValidity());
        assertTrue(result.getExecutionPath().contains("Set Status Invalid"));
    }
    
    @Test
    void testValidScenario() {
        SimulationInput input = new SimulationInput(false, 25.0);
        SimulationResult result = processEngine.execute(input);
        
        assertEquals("Valid", result.getFinalStatus());
        assertEquals("Valid", result.getDmnResult().getQuoteValidity());
        assertTrue(result.getExecutionPath().contains("Set Status Valid"));
    }
    
    @Test
    void testExecutionPath() {
        SimulationInput input = new SimulationInput(false, 25.0);
        SimulationResult result = processEngine.execute(input);
        
        assertTrue(result.getExecutionPath().contains("Start"));
        assertTrue(result.getExecutionPath().contains("Prepare Values for DMN"));
        assertTrue(result.getExecutionPath().contains("Look-up Results"));
        assertTrue(result.getExecutionPath().contains("Result / Decision Gateway"));
        assertTrue(result.getExecutionPath().contains("End"));
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
}

