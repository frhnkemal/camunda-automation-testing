package com.camunda.simulator.service;

import com.camunda.simulator.model.DMNResult;
import com.camunda.simulator.model.SimulationInput;
import com.camunda.simulator.model.SimulationResult;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import java.io.InputStream;
import java.util.*;


/**
 * Simulates process execution by parsing and executing actual BPMN files.
 */
public class ProcessEngine {
    
    private final DMNEvaluator dmnEvaluator;
    private final FileManager fileManager;
    private BpmnModelInstance bpmnModelInstance;
    
    public ProcessEngine(FileManager fileManager, DMNEvaluator dmnEvaluator) {
        this.fileManager = fileManager;
        this.dmnEvaluator = dmnEvaluator;
        loadBpmnFile();
    }
    
    /**
     * Load the BPMN file from FileManager.
     */
    public void loadBpmnFile() {
        InputStream bpmnStream = fileManager.getDefaultBpmnFile();
        if (bpmnStream != null) {
            try {
                this.bpmnModelInstance = Bpmn.readModelFromStream(bpmnStream);
            } catch (Exception e) {
                System.err.println("Failed to load BPMN file: " + e.getMessage());
                this.bpmnModelInstance = null;
            }
        } else {
            this.bpmnModelInstance = null;
        }
    }
    
    /**
     * Execute the process using the uploaded BPMN file.
     * Falls back to hardcoded logic if no BPMN file is available.
     */
    public SimulationResult execute(SimulationInput inputs) {
        if (bpmnModelInstance != null) {
            return executeBpmnProcess(inputs);
        } else {
            return executeDefaultProcess(inputs);
        }
    }
    
    /**
     * Execute process from BPMN file.
     */
    private SimulationResult executeBpmnProcess(SimulationInput inputs) {
        List<String> executionPath = new ArrayList<>();
        Map<String, Object> processVariables = new HashMap<>();
        
        // Initialize process variables from inputs
        processVariables.put("bi_dealMarginPercent", inputs.getDealMarginPercent());
        processVariables.put("bi_manualPriceCost", inputs.getManualPriceCost());
        
        // Find the process definition
        Collection<org.camunda.bpm.model.bpmn.instance.Process> processes = bpmnModelInstance.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class);
        if (processes.isEmpty()) {
            return executeDefaultProcess(inputs);
        }
        
        org.camunda.bpm.model.bpmn.instance.Process process = processes.iterator().next();
        
        // Find start event
        StartEvent startEvent = findStartEvent(process);
        if (startEvent == null) {
            return executeDefaultProcess(inputs);
        }
        
        executionPath.add(startEvent.getName() != null ? startEvent.getName() : "Start");
        
        // Traverse the process flow
        FlowNode currentNode = getOutgoingFlowNode(startEvent);
        DMNResult dmnResult = null;
        String finalStatus = null;
        
        while (currentNode != null) {
            String nodeName = getNodeName(currentNode);
            executionPath.add(nodeName);
            
            // Handle different node types
            if (currentNode instanceof ServiceTask) {
                ServiceTask serviceTask = (ServiceTask) currentNode;
                handleServiceTask(serviceTask, processVariables);
                
                // Check if it's a DMN task
                if (isDmnTask(serviceTask)) {
                    dmnResult = evaluateDmnTask(serviceTask, processVariables);
                    if (dmnResult != null) {
                        processVariables.put("quoteValidity", dmnResult.getQuoteValidity());
                    }
                }
            } else if (currentNode instanceof BusinessRuleTask) {
                // Handle BusinessRuleTask (DMN decision tasks)
                BusinessRuleTask businessRuleTask = (BusinessRuleTask) currentNode;
                dmnResult = evaluateBusinessRuleTask(businessRuleTask, processVariables);
                if (dmnResult != null) {
                    processVariables.put("quoteValidity", dmnResult.getQuoteValidity());
                }
            } else if (currentNode instanceof ExclusiveGateway) {
                ExclusiveGateway gateway = (ExclusiveGateway) currentNode;
                currentNode = evaluateGateway(gateway, processVariables);
                if (currentNode == null) {
                    break;
                }
                continue; // Skip adding the next node twice
            } else if (currentNode instanceof EndEvent) {
                // Extract final status from process variables
                finalStatus = (String) processVariables.getOrDefault("cim_Status", "Completed");
                break;
            }
            
            currentNode = getOutgoingFlowNode(currentNode);
        }
        
        // If no DMN result was found, try to evaluate using DMN file
        if (dmnResult == null) {
            try {
                // Prepare variables for DMN (use mapped variables if available)
                Map<String, Object> dmnVars = new HashMap<>();
                if (processVariables.containsKey("manualPriceCost")) {
                    dmnVars.put("manualPriceCost", processVariables.get("manualPriceCost"));
                } else if (processVariables.containsKey("bi_manualPriceCost")) {
                    dmnVars.put("manualPriceCost", processVariables.get("bi_manualPriceCost"));
                } else {
                    dmnVars.put("manualPriceCost", inputs.getManualPriceCost());
                }
                
                if (processVariables.containsKey("dealMarginPercent")) {
                    dmnVars.put("dealMarginPercent", processVariables.get("dealMarginPercent"));
                } else if (processVariables.containsKey("bi_dealMarginPercent")) {
                    dmnVars.put("dealMarginPercent", processVariables.get("bi_dealMarginPercent"));
                } else {
                    dmnVars.put("dealMarginPercent", inputs.getDealMarginPercent());
                }
                
                dmnResult = dmnEvaluator.evaluate(dmnEvaluator.getDecisionKey(), dmnVars);
                processVariables.put("quoteValidity", dmnResult.getQuoteValidity());
            } catch (Exception e) {
                System.err.println("Failed to evaluate DMN: " + e.getMessage());
                // If DMN evaluation fails, we can't proceed - this is a design-time simulator
                // so we should fail fast rather than use incorrect logic
                throw new RuntimeException("DMN evaluation failed. Please ensure a valid DMN file is uploaded.", e);
            }
        }
        
        // If no final status, determine from quoteValidity
        if (finalStatus == null) {
            finalStatus = dmnResult.getQuoteValidity();
            processVariables.put("cim_Status", finalStatus);
        }
        
        return new SimulationResult(
            inputs,
            dmnResult,
            executionPath,
            finalStatus,
            processVariables
        );
    }
    
    /**
     * Execute default hardcoded process (fallback).
     */
    private SimulationResult executeDefaultProcess(SimulationInput inputs) {
        List<String> executionPath = new ArrayList<>();
        Map<String, Object> processVariables = new HashMap<>();
        
        // Step 1: Start
        executionPath.add("Start");
        
        // Step 2: Prepare Values for DMN (mock service task)
        executionPath.add("Prepare Values for DMN");
        processVariables.put("bi_dealMarginPercent", inputs.getDealMarginPercent());
        processVariables.put("bi_manualPriceCost", inputs.getManualPriceCost());
        
        // Step 3: Look-up Results (DMN decision call)
        executionPath.add("Look-up Results");
        DMNResult dmnResult = dmnEvaluator.evaluateEntryLevelExercise(
            inputs.getManualPriceCost(),
            inputs.getDealMarginPercent()
        );
        processVariables.put("quoteValidity", dmnResult.getQuoteValidity());
        
        // Step 4: Result / Decision Gateway
        executionPath.add("Result / Decision Gateway");
        
        // Step 5: Route based on quoteValidity
        String finalStatus;
        if ("Invalid".equals(dmnResult.getQuoteValidity())) {
            executionPath.add("Set Status Invalid");
            finalStatus = "Invalid";
            processVariables.put("cim_Status", "Invalid");
        } else {
            executionPath.add("Set Status Valid");
            finalStatus = "Valid";
            processVariables.put("cim_Status", "Valid");
        }
        
        // Step 6: End
        executionPath.add("End");
        
        return new SimulationResult(
            inputs,
            dmnResult,
            executionPath,
            finalStatus,
            processVariables
        );
    }
    
    /**
     * Find the start event in a process.
     */
    private StartEvent findStartEvent(org.camunda.bpm.model.bpmn.instance.Process process) {
        Collection<StartEvent> startEvents = bpmnModelInstance.getModelElementsByType(StartEvent.class);
        // Filter to only start events in this process
        for (StartEvent startEvent : startEvents) {
            if (startEvent.getParentElement() == process || 
                startEvent.getScope() == process) {
                return startEvent;
            }
        }
        return startEvents.isEmpty() ? null : startEvents.iterator().next();
    }
    
    /**
     * Get the outgoing flow node from a flow node.
     */
    private FlowNode getOutgoingFlowNode(FlowNode node) {
        Collection<SequenceFlow> outgoingFlows = node.getOutgoing();
        if (outgoingFlows.isEmpty()) {
            return null;
        }
        SequenceFlow flow = outgoingFlows.iterator().next();
        return (FlowNode) flow.getTarget();
    }
    
    /**
     * Get node name for display.
     */
    private String getNodeName(FlowNode node) {
        if (node.getName() != null && !node.getName().isEmpty()) {
            return node.getName();
        }
        if (node instanceof StartEvent) return "Start";
        if (node instanceof EndEvent) return "End";
        if (node instanceof ServiceTask) return "Service Task";
        if (node instanceof ExclusiveGateway) return "Gateway";
        return node.getId();
    }
    
    /**
     * Check if a service task is a DMN task.
     */
    private boolean isDmnTask(ServiceTask task) {
        String implementation = task.getImplementation();
        String type = task.getCamundaType();
        String name = task.getName();
        // Check if it's a DMN task by type, implementation, or name
        return "dmn".equals(type) || 
               (implementation != null && implementation.contains("dmn")) ||
               (name != null && (name.toLowerCase().contains("dmn") || 
                                 name.toLowerCase().contains("decision") ||
                                 name.toLowerCase().contains("look-up")));
    }
    
    /**
     * Handle a service task execution.
     */
    private void handleServiceTask(ServiceTask task, Map<String, Object> variables) {
        String taskName = task.getName();
        
        // Handle "Prepare Values for DMN" task - map variables
        if (taskName != null && taskName.contains("Prepare Values")) {
            // Map bi_ variables to regular variables for DMN
            // Based on the BPMN: bi_dealMarginPercent -> dealMarginPercent, bi_manualPriceCost -> manualPriceCost
            if (variables.containsKey("bi_dealMarginPercent")) {
                variables.put("dealMarginPercent", variables.get("bi_dealMarginPercent"));
            }
            if (variables.containsKey("bi_manualPriceCost")) {
                variables.put("manualPriceCost", variables.get("bi_manualPriceCost"));
            }
            System.out.println("Mapped variables for DMN: dealMarginPercent=" + variables.get("dealMarginPercent") + 
                             ", manualPriceCost=" + variables.get("manualPriceCost"));
        }
        
        // Handle "Set Status" tasks - set cim_Status variable
        if (taskName != null && taskName.contains("Set Status")) {
            // Extract status from task name or determine from context
            if (taskName.contains("3000") || taskName.contains("Valid")) {
                variables.put("cim_Status", "Valid");
            } else if (taskName.contains("4000") || taskName.contains("Invalid")) {
                variables.put("cim_Status", "Invalid");
            }
        }
    }
    
    /**
     * Evaluate a DMN task.
     */
    private DMNResult evaluateDmnTask(ServiceTask task, Map<String, Object> variables) {
        // Ensure variables are mapped correctly for DMN
        // DMN expects: manualPriceCost, dealMarginPercent (not bi_*)
        Map<String, Object> dmnVariables = new HashMap<>();
        if (variables.containsKey("manualPriceCost")) {
            dmnVariables.put("manualPriceCost", variables.get("manualPriceCost"));
        } else if (variables.containsKey("bi_manualPriceCost")) {
            dmnVariables.put("manualPriceCost", variables.get("bi_manualPriceCost"));
        }
        
        if (variables.containsKey("dealMarginPercent")) {
            dmnVariables.put("dealMarginPercent", variables.get("dealMarginPercent"));
        } else if (variables.containsKey("bi_dealMarginPercent")) {
            dmnVariables.put("dealMarginPercent", variables.get("bi_dealMarginPercent"));
        }
        
        System.out.println("Evaluating DMN with variables: " + dmnVariables);
        
        return dmnEvaluator.evaluate(dmnEvaluator.getDecisionKey(), dmnVariables);
    }
    
    /**
     * Evaluate a BusinessRuleTask (DMN decision task).
     */
    private DMNResult evaluateBusinessRuleTask(BusinessRuleTask task, Map<String, Object> variables) {
        // Extract decision ID from zeebe:calledDecision extension element
        // For now, use the default decision key from the uploaded DMN file
        String decisionKey = dmnEvaluator.getDecisionKey();
        
        // Ensure variables are mapped correctly for DMN
        // DMN expects: manualPriceCost, dealMarginPercent (not bi_*)
        Map<String, Object> dmnVariables = new HashMap<>();
        if (variables.containsKey("manualPriceCost")) {
            dmnVariables.put("manualPriceCost", variables.get("manualPriceCost"));
        } else if (variables.containsKey("bi_manualPriceCost")) {
            dmnVariables.put("manualPriceCost", variables.get("bi_manualPriceCost"));
        }
        
        if (variables.containsKey("dealMarginPercent")) {
            dmnVariables.put("dealMarginPercent", variables.get("dealMarginPercent"));
        } else if (variables.containsKey("bi_dealMarginPercent")) {
            dmnVariables.put("dealMarginPercent", variables.get("bi_dealMarginPercent"));
        }
        
        System.out.println("Evaluating DMN with variables: " + dmnVariables);
        
        return dmnEvaluator.evaluate(decisionKey, dmnVariables);
    }
    
    /**
     * Evaluate a gateway and return the next flow node.
     */
    private FlowNode evaluateGateway(ExclusiveGateway gateway, Map<String, Object> variables) {
        Collection<SequenceFlow> outgoingFlows = gateway.getOutgoing();
        SequenceFlow defaultFlow = null;
        
        // First, identify the default flow and collect all conditional flows
        List<SequenceFlow> conditionalFlows = new ArrayList<>();
        for (SequenceFlow flow : outgoingFlows) {
            String conditionExpression = flow.getConditionExpression() != null ? 
                flow.getConditionExpression().getTextContent() : null;
            
            if (conditionExpression == null || conditionExpression.isEmpty()) {
                // This is the default flow (no condition)
                defaultFlow = flow;
            } else {
                // This is a conditional flow
                conditionalFlows.add(flow);
            }
        }
        
        // First, check all conditional flows
        for (SequenceFlow flow : conditionalFlows) {
            String conditionExpression = flow.getConditionExpression().getTextContent();
            
            // Evaluate condition
            if (evaluateCondition(conditionExpression, variables)) {
                System.out.println("Gateway condition matched: " + conditionExpression + " -> " + getNodeName((FlowNode) flow.getTarget()));
                return (FlowNode) flow.getTarget();
            }
        }
        
        // If no condition matched, use the default flow
        if (defaultFlow != null) {
            System.out.println("Using default flow -> " + getNodeName((FlowNode) defaultFlow.getTarget()));
            return (FlowNode) defaultFlow.getTarget();
        }
        
        // Fallback: return first flow if no default is specified
        return outgoingFlows.isEmpty() ? null : (FlowNode) outgoingFlows.iterator().next().getTarget();
    }
    
    /**
     * Evaluate a condition expression (simplified implementation).
     */
    private boolean evaluateCondition(String expression, Map<String, Object> variables) {
        if (expression == null || expression.trim().isEmpty()) {
            return false; // Empty conditions are handled as default flows
        }
        
        // Remove leading = if present (Zeebe/Camunda format: =expression)
        String cleanExpression = expression.trim();
        if (cleanExpression.startsWith("=")) {
            cleanExpression = cleanExpression.substring(1).trim();
        }
        
        System.out.println("Evaluating condition: " + cleanExpression);
        
        // Check for common patterns like: quoteValidity = "Invalid" or quoteValidity == "Invalid"
        if (cleanExpression.contains("quoteValidity")) {
            String quoteValidity = (String) variables.get("quoteValidity");
            System.out.println("  quoteValidity value: " + quoteValidity);
            
            // Handle both = and == operators
            if (cleanExpression.contains("= \"Invalid\"") || cleanExpression.contains("== \"Invalid\"")) {
                boolean result = "Invalid".equals(quoteValidity);
                System.out.println("  Condition result: " + result);
                return result;
            }
            if (cleanExpression.contains("= \"Valid\"") || cleanExpression.contains("== \"Valid\"")) {
                boolean result = "Valid".equals(quoteValidity);
                System.out.println("  Condition result: " + result);
                return result;
            }
            
            // Handle without quotes: quoteValidity = Invalid
            if (cleanExpression.contains("= Invalid") || cleanExpression.contains("== Invalid")) {
                boolean result = "Invalid".equals(quoteValidity);
                System.out.println("  Condition result: " + result);
                return result;
            }
            if (cleanExpression.contains("= Valid") || cleanExpression.contains("== Valid")) {
                boolean result = "Valid".equals(quoteValidity);
                System.out.println("  Condition result: " + result);
                return result;
            }
        }
        
        // Try to evaluate as a simple expression
        // For now, return false if we can't evaluate
        System.out.println("  Could not evaluate condition, returning false");
        return false;
    }
}

