package com.camunda.simulator.model;

/**
 * Pre-defined test scenario.
 */
public class TestScenario {
    private String name;
    private String description;
    private SimulationInput inputs;
    private String expectedResult; // "Valid" or "Invalid"
    
    public TestScenario() {
    }
    
    public TestScenario(String name, String description, SimulationInput inputs, String expectedResult) {
        this.name = name;
        this.description = description;
        this.inputs = inputs;
        this.expectedResult = expectedResult;
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
}

