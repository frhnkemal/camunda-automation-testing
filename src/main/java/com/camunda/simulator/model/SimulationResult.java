package com.camunda.simulator.model;

import java.util.List;
import java.util.Map;

/**
 * Complete simulation result.
 */
public class SimulationResult {
    private SimulationInput inputs;
    private DMNResult dmnResult;
    private List<String> executionPath;
    private String finalStatus; // "Valid" or "Invalid"
    private Map<String, Object> processVariables;
    
    public SimulationResult() {
    }
    
    public SimulationResult(SimulationInput inputs, DMNResult dmnResult, 
                           List<String> executionPath, String finalStatus, 
                           Map<String, Object> processVariables) {
        this.inputs = inputs;
        this.dmnResult = dmnResult;
        this.executionPath = executionPath;
        this.finalStatus = finalStatus;
        this.processVariables = processVariables;
    }
    
    public SimulationInput getInputs() {
        return inputs;
    }
    
    public void setInputs(SimulationInput inputs) {
        this.inputs = inputs;
    }
    
    public DMNResult getDmnResult() {
        return dmnResult;
    }
    
    public void setDmnResult(DMNResult dmnResult) {
        this.dmnResult = dmnResult;
    }
    
    public List<String> getExecutionPath() {
        return executionPath;
    }
    
    public void setExecutionPath(List<String> executionPath) {
        this.executionPath = executionPath;
    }
    
    public String getFinalStatus() {
        return finalStatus;
    }
    
    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }
    
    public Map<String, Object> getProcessVariables() {
        return processVariables;
    }
    
    public void setProcessVariables(Map<String, Object> processVariables) {
        this.processVariables = processVariables;
    }
}

