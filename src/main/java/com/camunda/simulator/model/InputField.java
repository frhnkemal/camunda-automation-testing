package com.camunda.simulator.model;

/**
 * Represents an input field requirement from a BPMN process.
 */
public class InputField {
    private String name;
    private String type; // "string", "number", "boolean", etc.
    private String label;
    private Object defaultValue;
    private boolean required;
    
    public InputField() {
    }
    
    public InputField(String name, String type, String label, Object defaultValue, boolean required) {
        this.name = name;
        this.type = type;
        this.label = label;
        this.defaultValue = defaultValue;
        this.required = required;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public Object getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public void setRequired(boolean required) {
        this.required = required;
    }
}

