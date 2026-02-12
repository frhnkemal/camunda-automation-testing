package com.camunda.simulator.model;

import java.util.List;

/**
 * Pre-defined test scenario.
 */
public class TestScenario {
    private String name;
    private String description;
    private SimulationInput inputs;
    private String expectedResult; // "Valid" or "Invalid"
    private List<String> expectedExecutionPath; // optional: key steps that must appear in order in actual path

    public TestScenario() {
    }

    public TestScenario(String name, String description, SimulationInput inputs, String expectedResult) {
        this(name, description, inputs, expectedResult, null);
    }

    public TestScenario(String name, String description, SimulationInput inputs, String expectedResult,
                       List<String> expectedExecutionPath) {
        this.name = name;
        this.description = description;
        this.inputs = inputs;
        this.expectedResult = expectedResult;
        this.expectedExecutionPath = expectedExecutionPath;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public SimulationInput getInputs() {
        return inputs;
    }
    
    public void setInputs(SimulationInput inputs) {
        this.inputs = inputs;
    }
    
    public String getExpectedResult() {
        return expectedResult;
    }
    
    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    public List<String> getExpectedExecutionPath() {
        return expectedExecutionPath;
    }

    public void setExpectedExecutionPath(List<String> expectedExecutionPath) {
        this.expectedExecutionPath = expectedExecutionPath;
    }
}

