package com.camunda.simulator.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages uploaded BPMN and DMN files in memory.
 */
public class FileManager {
    
    private final Map<String, byte[]> bpmnFiles = new ConcurrentHashMap<>();
    private final Map<String, byte[]> dmnFiles = new ConcurrentHashMap<>();
    
    /**
     * Store a BPMN file.
     * @param filename The name of the file
     * @param content The file content
     */
    public void storeBpmnFile(String filename, byte[] content) {
        bpmnFiles.put(filename, content);
    }
    
    /**
     * Store a DMN file.
     * @param filename The name of the file
     * @param content The file content
     */
    public void storeDmnFile(String filename, byte[] content) {
        dmnFiles.put(filename, content);
    }
    
    /**
     * Get a BPMN file as InputStream.
     * @param filename The name of the file
     * @return InputStream of the file, or null if not found
     */
    public InputStream getBpmnFile(String filename) {
        byte[] content = bpmnFiles.get(filename);
        if (content == null) {
            return null;
        }
        return new ByteArrayInputStream(content);
    }
    
    /**
     * Get a DMN file as InputStream.
     * @param filename The name of the file
     * @return InputStream of the file, or null if not found
     */
    public InputStream getDmnFile(String filename) {
        byte[] content = dmnFiles.get(filename);
        if (content == null) {
            return null;
        }
        return new ByteArrayInputStream(content);
    }
    
    /**
     * Get the default BPMN file (first one if available).
     * @return InputStream of the file, or null if no files available
     */
    public InputStream getDefaultBpmnFile() {
        if (bpmnFiles.isEmpty()) {
            return null;
        }
        return getBpmnFile(bpmnFiles.keySet().iterator().next());
    }
    
    /**
     * Get the default DMN file (first one if available).
     * @return InputStream of the file, or null if no files available
     */
    public InputStream getDefaultDmnFile() {
        if (dmnFiles.isEmpty()) {
            return null;
        }
        return getDmnFile(dmnFiles.keySet().iterator().next());
    }
    
    /**
     * Check if any BPMN files are stored.
     */
    public boolean hasBpmnFiles() {
        return !bpmnFiles.isEmpty();
    }
    
    /**
     * Check if any DMN files are stored.
     */
    public boolean hasDmnFiles() {
        return !dmnFiles.isEmpty();
    }
    
    /**
     * Get all BPMN file names.
     */
    public java.util.Set<String> getBpmnFileNames() {
        return bpmnFiles.keySet();
    }
    
    /**
     * Get all DMN file names.
     */
    public java.util.Set<String> getDmnFileNames() {
        return dmnFiles.keySet();
    }
    
    /**
     * Remove a BPMN file.
     */
    public void removeBpmnFile(String filename) {
        bpmnFiles.remove(filename);
    }
    
    /**
     * Remove a DMN file.
     */
    public void removeDmnFile(String filename) {
        dmnFiles.remove(filename);
    }
    
    /**
     * Clear all stored files.
     */
    public void clearAll() {
        bpmnFiles.clear();
        dmnFiles.clear();
    }
}

