package com.camunda.simulator.service;

import com.camunda.simulator.model.DMNResult;
import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.DmnEngineConfiguration;
import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.dmn.engine.DmnDecisionResultEntries;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * Evaluates DMN decisions by parsing and executing actual DMN files.
 */
public class DMNEvaluator {
    
    private final DmnEngine dmnEngine;
    private final FileManager fileManager;
    private DmnModelInstance dmnModelInstance;
    private String decisionKey;
    
    public DMNEvaluator(FileManager fileManager) {
        this.fileManager = fileManager;
        this.dmnEngine = DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine();
        loadDmnFile();
    }
    
    /**
     * Load the DMN file from FileManager.
     * Always reloads to ensure we have the latest version.
     */
    public void loadDmnFile() {
        InputStream dmnStream = fileManager.getDefaultDmnFile();
        if (dmnStream != null) {
            try {
                // Always create a new model instance to ensure we have the latest version
                this.dmnModelInstance = Dmn.readModelFromStream(dmnStream);
                // Extract decision key from the DMN model
                this.decisionKey = extractDecisionKey(dmnModelInstance);
                System.out.println("DMN file reloaded. Decision key: " + this.decisionKey);
            } catch (Exception e) {
                System.err.println("Failed to load DMN file: " + e.getMessage());
                e.printStackTrace();
                this.dmnModelInstance = null;
                this.decisionKey = null;
            } finally {
                try {
                    dmnStream.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        } else {
            System.out.println("No DMN file available in FileManager");
            this.dmnModelInstance = null;
            this.decisionKey = null;
        }
    }
    
    /**
     * Extract the decision key from the DMN model.
     */
    private String extractDecisionKey(DmnModelInstance modelInstance) {
        Collection<Decision> decisions = modelInstance.getModelElementsByType(Decision.class);
        if (decisions.isEmpty()) {
            return null;
        }
        Decision decision = decisions.iterator().next();
        String key = decision.getId() != null ? decision.getId() : decision.getName();
        
        // Log decision table rules for debugging
        try {
            org.camunda.bpm.model.dmn.instance.DecisionTable decisionTable = 
                modelInstance.getModelElementsByType(org.camunda.bpm.model.dmn.instance.DecisionTable.class)
                    .stream().findFirst().orElse(null);
            if (decisionTable != null) {
                System.out.println("DMN Decision Table Rules:");
                Collection<org.camunda.bpm.model.dmn.instance.Rule> rules = 
                    modelInstance.getModelElementsByType(org.camunda.bpm.model.dmn.instance.Rule.class);
                for (org.camunda.bpm.model.dmn.instance.Rule rule : rules) {
                    Collection<org.camunda.bpm.model.dmn.instance.InputEntry> inputEntries = 
                        rule.getInputEntries();
                    Collection<org.camunda.bpm.model.dmn.instance.OutputEntry> outputEntries = 
                        rule.getOutputEntries();
                    StringBuilder ruleDesc = new StringBuilder("  Rule: ");
                    for (org.camunda.bpm.model.dmn.instance.InputEntry entry : inputEntries) {
                        String text = entry.getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            ruleDesc.append(text).append(" ");
                        }
                    }
                    ruleDesc.append("-> ");
                    for (org.camunda.bpm.model.dmn.instance.OutputEntry entry : outputEntries) {
                        String text = entry.getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            ruleDesc.append(text);
                        }
                    }
                    System.out.println(ruleDesc.toString());
                }
            }
        } catch (Exception e) {
            System.out.println("Could not extract DMN rules for logging: " + e.getMessage());
        }
        
        return key;
    }
    
    /**
     * Evaluate a DMN decision using the uploaded DMN file.
     * Always uses the DMN file logic if available. Throws exception if DMN file is not loaded.
     * 
     * @param decisionKey The decision key/ID to evaluate (uses current decisionKey if null)
     * @param variables The input variables for the decision
     * @return DMNResult containing the decision output
     * @throws IllegalStateException if no DMN file is loaded
     */
    public DMNResult evaluate(String decisionKey, Map<String, Object> variables) {
        // Always reload DMN file to ensure we have the latest version (no caching)
        // This ensures any changes to the uploaded DMN file are immediately reflected
        DmnModelInstance oldInstance = this.dmnModelInstance;
        loadDmnFile();
        
        // Log if we got a new instance
        if (oldInstance != this.dmnModelInstance) {
            System.out.println("DMN model instance changed - new file loaded");
        }
        
        if (dmnModelInstance == null) {
            throw new IllegalStateException("No DMN file is loaded. Please upload a DMN file first.");
        }
        
        // Use provided decisionKey or fall back to the extracted one
        String keyToUse = decisionKey != null ? decisionKey : this.decisionKey;
        if (keyToUse == null) {
            throw new IllegalStateException("No decision key available. DMN file may be invalid.");
        }
        
        try {
            System.out.println("Evaluating DMN decision: " + keyToUse + " with variables: " + variables);
            System.out.println("DMN model instance hash: " + (dmnModelInstance != null ? dmnModelInstance.hashCode() : "null"));
            
            // Evaluate the decision using the actual DMN file
            DmnDecisionResult decisionResult = dmnEngine.evaluateDecision(keyToUse, dmnModelInstance, variables);
            
            // Extract the result
            if (decisionResult.isEmpty()) {
                System.err.println("Warning: DMN decision returned no results");
                return new DMNResult("No result");
            }
            
            // Get the first result entry
            DmnDecisionResultEntries result = decisionResult.getFirstResult();
            if (result.isEmpty()) {
                System.err.println("Warning: DMN decision result is empty");
                return new DMNResult("No result");
            }
            
            // Extract quoteValidity or the first output value
            Object quoteValidityObj = result.getEntry("quoteValidity");
            String quoteValidity = null;
            if (quoteValidityObj != null) {
                quoteValidity = quoteValidityObj.toString();
                // Remove quotes if present
                if (quoteValidity.startsWith("\"") && quoteValidity.endsWith("\"")) {
                    quoteValidity = quoteValidity.substring(1, quoteValidity.length() - 1);
                }
            }
            
            if (quoteValidity == null) {
                // Try to get any output value
                if (!result.isEmpty()) {
                    // Get the first entry value
                    for (String key : result.keySet()) {
                        Object value = result.getEntry(key);
                        if (value != null) {
                            quoteValidity = value.toString();
                            // Remove quotes if present
                            if (quoteValidity.startsWith("\"") && quoteValidity.endsWith("\"")) {
                                quoteValidity = quoteValidity.substring(1, quoteValidity.length() - 1);
                            }
                            break;
                        }
                    }
                }
                if (quoteValidity == null) {
                    quoteValidity = "No result";
                }
            }
            
            System.out.println("DMN evaluation result: " + quoteValidity);
            return new DMNResult(quoteValidity);
        } catch (Exception e) {
            System.err.println("Error evaluating DMN decision: " + e.getMessage());
            e.printStackTrace();
            // Re-throw the exception instead of falling back to hardcoded logic
            throw new RuntimeException("Failed to evaluate DMN decision: " + e.getMessage(), e);
        }
    }
    
    /**
     * Evaluate the entry_level_camunda_exercise_v1_0 DMN decision.
     * Always uses the uploaded DMN file logic. No hardcoded fallback.
     * 
     * @param manualPriceCost The manual price cost flag
     * @param dealMarginPercent The deal margin percent value
     * @return DMNResult from the actual DMN file evaluation
     * @throws IllegalStateException if no DMN file is loaded
     */
    public DMNResult evaluateEntryLevelExercise(Boolean manualPriceCost, Double dealMarginPercent) {
        // Use the actual variable names expected by the DMN file
        // The DMN file expects: manualPriceCost and dealMarginPercent (not bi_*)
        Map<String, Object> variables = Map.of(
            "manualPriceCost", manualPriceCost != null ? manualPriceCost : false,
            "dealMarginPercent", dealMarginPercent != null ? dealMarginPercent : 0.0
        );
        
        // Always use the DMN file - no fallback to hardcoded logic
        return evaluate(decisionKey, variables);
    }
    
    /**
     * Get the current decision key.
     */
    public String getDecisionKey() {
        return decisionKey;
    }
    
    /**
     * Check if a DMN file is loaded.
     */
    public boolean isDmnLoaded() {
        return dmnModelInstance != null && decisionKey != null;
    }
}

