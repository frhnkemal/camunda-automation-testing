package com.camunda.simulator.model;

/**
 * Result from DMN decision evaluation.
 */
public class DMNResult {
    private String quoteValidity; // "Valid" or "Invalid"
    
    public DMNResult() {
    }
    
    public DMNResult(String quoteValidity) {
        this.quoteValidity = quoteValidity;
    }
    
    public String getQuoteValidity() {
        return quoteValidity;
    }
    
    public void setQuoteValidity(String quoteValidity) {
        this.quoteValidity = quoteValidity;
    }
}

