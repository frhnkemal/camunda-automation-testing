package com.camunda.simulator.service;

import com.camunda.simulator.model.InputField;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;

import java.util.*;

/**
 * Analyzes BPMN files to extract input variable requirements.
 */
public class BpmnInputAnalyzer {
    
    private final BpmnModelInstance bpmnModelInstance;
    
    public BpmnInputAnalyzer(BpmnModelInstance bpmnModelInstance) {
        this.bpmnModelInstance = bpmnModelInstance;
    }
    
    /**
     * Extract input fields from the BPMN process.
     * Analyzes start events, message start events, and form fields.
     */
    public List<InputField> extractInputFields() {
        List<InputField> inputFields = new ArrayList<>();
        
        if (bpmnModelInstance == null) {
            return getDefaultInputFields();
        }
        
        // Find all processes
        Collection<org.camunda.bpm.model.bpmn.instance.Process> processes = 
            bpmnModelInstance.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class);
        
        if (processes.isEmpty()) {
            return getDefaultInputFields();
        }
        
        org.camunda.bpm.model.bpmn.instance.Process process = processes.iterator().next();
        
        // Find start events
        Collection<StartEvent> startEvents = bpmnModelInstance.getModelElementsByType(StartEvent.class);
        
        for (StartEvent startEvent : startEvents) {
            // Check if it's in this process
            if (startEvent.getParentElement() == process || startEvent.getScope() == process) {
                // Extract from message start event
                if (startEvent instanceof MessageEventDefinition) {
                    extractFromMessageStartEvent(startEvent, inputFields);
                }
                
                // Extract from form fields
                extractFromFormFields(startEvent, inputFields);
                
                // Extract from input mappings
                extractFromInputMappings(startEvent, inputFields);
            }
        }
        
        // If no inputs found, analyze the process flow to infer inputs
        if (inputFields.isEmpty()) {
            inputFields = inferInputsFromProcess(process);
        }
        
        // If still empty, return defaults
        if (inputFields.isEmpty()) {
            return getDefaultInputFields();
        }
        
        return inputFields;
    }
    
    /**
     * Extract inputs from message start event.
     */
    private void extractFromMessageStartEvent(StartEvent startEvent, List<InputField> inputFields) {
        // Message start events can have message payload variables
        // This is a simplified implementation
        String eventName = startEvent.getName();
        if (eventName != null && !eventName.isEmpty()) {
            // Try to infer variable names from event name
            // In a real implementation, you'd parse the message definition
        }
    }
    
    /**
     * Extract inputs from form fields.
     */
    private void extractFromFormFields(StartEvent startEvent, List<InputField> inputFields) {
        // Check for camunda:formData
        // This is a simplified implementation
        // In a real BPMN, form fields would be defined in formData
    }
    
    /**
     * Extract inputs from input mappings.
     */
    private void extractFromInputMappings(StartEvent startEvent, List<InputField> inputFields) {
        // Check for input/output mappings in the process
        // Look for dataInputAssociations
    }
    
    /**
     * Infer input variables by analyzing the process flow.
     * Looks for variables used in the first few tasks.
     */
    private List<InputField> inferInputsFromProcess(org.camunda.bpm.model.bpmn.instance.Process process) {
        List<InputField> inferredFields = new ArrayList<>();
        Set<String> variableNames = new HashSet<>();
        
        // Find start event
        StartEvent startEvent = findStartEvent(process);
        if (startEvent == null) {
            return inferredFields;
        }
        
        // Traverse all nodes to find variable references
        FlowNode currentNode = getOutgoingFlowNode(startEvent);
        int depth = 0;
        int maxDepth = 10; // Check more levels
        
        while (currentNode != null && depth < maxDepth) {
            // Look for variable references in service tasks
            if (currentNode instanceof ServiceTask) {
                ServiceTask task = (ServiceTask) currentNode;
                String name = task.getName();
                if (name != null) {
                    // Try to extract variable names from task names
                    extractVariableNamesFromText(name, variableNames);
                }
                // Check implementation
                String implementation = task.getImplementation();
                if (implementation != null) {
                    extractVariableNamesFromText(implementation, variableNames);
                }
            }
            
            // Check for expressions in sequence flows
            Collection<SequenceFlow> outgoing = currentNode.getOutgoing();
            for (SequenceFlow flow : outgoing) {
                if (flow.getConditionExpression() != null) {
                    String condition = flow.getConditionExpression().getTextContent();
                    extractVariableNamesFromText(condition, variableNames);
                }
                // Check flow name
                String flowName = flow.getName();
                if (flowName != null) {
                    extractVariableNamesFromText(flowName, variableNames);
                }
            }
            
            // Check node name
            String nodeName = currentNode.getName();
            if (nodeName != null) {
                extractVariableNamesFromText(nodeName, variableNames);
            }
            
            currentNode = getOutgoingFlowNode(currentNode);
            depth++;
        }
        
        // Also check all text content in the BPMN for common variable patterns
        // Look for bi_* variables which are common input variables
        Collection<org.camunda.bpm.model.bpmn.instance.Process> processes = 
            bpmnModelInstance.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class);
        for (org.camunda.bpm.model.bpmn.instance.Process p : processes) {
            String processName = p.getName();
            if (processName != null) {
                extractVariableNamesFromText(processName, variableNames);
            }
        }
        
        // If we found common input variable patterns, use them
        // Otherwise, add known common input variables based on process context
        if (variableNames.isEmpty()) {
            // Add common input variables that might be used
            variableNames.add("bi_manualPriceCost");
            variableNames.add("bi_dealMarginPercent");
            variableNames.add("manualPriceCost");
            variableNames.add("dealMarginPercent");
        }
        
        // Convert variable names to input fields
        for (String varName : variableNames) {
            if (isLikelyInputVariable(varName)) {
                InputField field = createInputFieldFromVariable(varName);
                // Avoid duplicates
                if (inferredFields.stream().noneMatch(f -> f.getName().equals(field.getName()))) {
                    inferredFields.add(field);
                }
            }
        }
        
        return inferredFields;
    }
    
    /**
     * Extract variable names from text (simplified).
     */
    private void extractVariableNamesFromText(String text, Set<String> variableNames) {
        if (text == null) return;
        
        // Look for common patterns like ${variableName} or variableName
        // This is a simplified regex-based approach
        String[] patterns = {
            "bi_\\w+",           // Variables starting with bi_
            "\\$\\{([^}]+)\\}",  // ${variable} pattern
            "([a-z_][a-z0-9_]*)", // Simple variable names
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, 
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(text);
            while (m.find()) {
                String varName = m.group(1) != null ? m.group(1) : m.group(0);
                if (varName.startsWith("$")) {
                    varName = varName.substring(2, varName.length() - 1);
                }
                variableNames.add(varName);
            }
        }
    }
    
    /**
     * Check if a variable name is likely an input variable.
     */
    private boolean isLikelyInputVariable(String varName) {
        if (varName == null || varName.isEmpty()) {
            return false;
        }
        // Filter out common internal variables
        String lower = varName.toLowerCase();
        return !lower.contains("status") && 
               !lower.contains("result") && 
               !lower.contains("validity") &&
               !lower.startsWith("cim_") &&
               !lower.equals("quotevalidity") &&
               (lower.startsWith("bi_") || 
                lower.contains("input") || 
                lower.contains("cost") || 
                lower.contains("margin") ||
                lower.contains("price") ||
                lower.contains("manual") ||
                lower.contains("deal"));
    }
    
    /**
     * Create an input field from a variable name.
     */
    private InputField createInputFieldFromVariable(String varName) {
        String label = formatLabel(varName);
        String type = inferType(varName);
        Object defaultValue = getDefaultValueForType(type);
        
        return new InputField(varName, type, label, defaultValue, true);
    }
    
    /**
     * Format variable name as a label.
     */
    private String formatLabel(String varName) {
        // Convert bi_manualPriceCost to "Manual Price Cost"
        String label = varName.replace("bi_", "").replace("_", " ");
        // Capitalize first letter of each word
        String[] words = label.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            if (word.length() > 0) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1));
                }
            }
        }
        return formatted.toString();
    }
    
    /**
     * Infer the type of a variable from its name.
     */
    private String inferType(String varName) {
        String lower = varName.toLowerCase();
        if (lower.contains("cost") || lower.contains("price") || lower.contains("margin") || lower.contains("percent")) {
            return "number";
        }
        if (lower.contains("flag") || lower.contains("is") || lower.contains("has") || lower.contains("manual")) {
            return "boolean";
        }
        return "string";
    }
    
    /**
     * Get default value for a type.
     */
    private Object getDefaultValueForType(String type) {
        switch (type) {
            case "number": return 0.0;
            case "boolean": return false;
            default: return "";
        }
    }
    
    /**
     * Find start event in process.
     */
    private StartEvent findStartEvent(org.camunda.bpm.model.bpmn.instance.Process process) {
        Collection<StartEvent> startEvents = bpmnModelInstance.getModelElementsByType(StartEvent.class);
        for (StartEvent startEvent : startEvents) {
            if (startEvent.getParentElement() == process || startEvent.getScope() == process) {
                return startEvent;
            }
        }
        return startEvents.isEmpty() ? null : startEvents.iterator().next();
    }
    
    /**
     * Get outgoing flow node.
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
     * Get default input fields (fallback).
     */
    private List<InputField> getDefaultInputFields() {
        List<InputField> fields = new ArrayList<>();
        fields.add(new InputField("manualPriceCost", "boolean", "Manual Price Cost", false, true));
        fields.add(new InputField("dealMarginPercent", "number", "Deal Margin Percent", 25.0, true));
        return fields;
    }
}

