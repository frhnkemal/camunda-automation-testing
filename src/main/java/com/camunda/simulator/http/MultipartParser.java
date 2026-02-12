package com.camunda.simulator.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple multipart/form-data parser for file uploads.
 */
public class MultipartParser {
    
    public static class ParsedFile {
        private final String filename;
        private final byte[] content;
        private final String contentType;
        
        public ParsedFile(String filename, byte[] content, String contentType) {
            this.filename = filename;
            this.content = content;
            this.contentType = contentType;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public byte[] getContent() {
            return content;
        }
        
        public String getContentType() {
            return contentType;
        }
    }
    
    /**
     * Parse multipart/form-data request.
     * @param body The request body
     * @param boundary The multipart boundary
     * @return Map of field names to parsed files
     */
    public static Map<String, ParsedFile> parse(byte[] body, String boundary) throws IOException {
        Map<String, ParsedFile> files = new HashMap<>();
        
        if (boundary == null || body == null) {
            return files;
        }
        
        String boundaryMarker = "--" + boundary;
        byte[] boundaryBytes = boundaryMarker.getBytes(StandardCharsets.UTF_8);
        
        int start = 0;
        while (true) {
            // Find boundary
            int boundaryPos = indexOf(body, boundaryBytes, start);
            if (boundaryPos == -1) {
                break;
            }
            
            // Find next boundary (or end)
            int nextBoundaryPos = indexOf(body, boundaryBytes, boundaryPos + boundaryBytes.length);
            if (nextBoundaryPos == -1) {
                break;
            }
            
            // Extract part between boundaries
            int partStart = boundaryPos + boundaryBytes.length;
            if (body[partStart] == '\r') partStart++;
            if (body[partStart] == '\n') partStart++;
            
            int partEnd = nextBoundaryPos;
            while (partEnd > partStart && (body[partEnd - 1] == '\n' || body[partEnd - 1] == '\r')) {
                partEnd--;
            }
            
            // Parse the part
            parsePart(body, partStart, partEnd, files);
            
            start = nextBoundaryPos;
        }
        
        return files;
    }
    
    private static void parsePart(byte[] body, int start, int end, Map<String, ParsedFile> files) {
        // Find header/body separator (double CRLF)
        int headerEnd = -1;
        for (int i = start; i < end - 3; i++) {
            if (body[i] == '\r' && body[i + 1] == '\n' && 
                body[i + 2] == '\r' && body[i + 3] == '\n') {
                headerEnd = i;
                break;
            }
        }
        
        if (headerEnd == -1) {
            return;
        }
        
        // Parse headers
        String headers = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
        String filename = extractFilename(headers);
        String contentType = extractContentType(headers);
        String fieldName = extractFieldName(headers);
        
        if (filename == null || fieldName == null) {
            return;
        }
        
        // Extract file content
        int contentStart = headerEnd + 4; // Skip CRLFCRLF
        int contentEnd = end;
        while (contentEnd > contentStart && (body[contentEnd - 1] == '\n' || body[contentEnd - 1] == '\r')) {
            contentEnd--;
        }
        
        byte[] content = new byte[contentEnd - contentStart];
        System.arraycopy(body, contentStart, content, 0, content.length);
        
        files.put(fieldName, new ParsedFile(filename, content, contentType));
    }
    
    private static String extractFilename(String headers) {
        int pos = headers.indexOf("filename=\"");
        if (pos == -1) {
            pos = headers.indexOf("filename=");
            if (pos == -1) return null;
            pos += 9;
        } else {
            pos += 10;
        }
        
        int end = headers.indexOf("\"", pos);
        if (end == -1) {
            end = headers.indexOf("\r\n", pos);
            if (end == -1) end = headers.indexOf("\n", pos);
            if (end == -1) end = headers.length();
        }
        
        return headers.substring(pos, end);
    }
    
    private static String extractContentType(String headers) {
        int pos = headers.indexOf("Content-Type:");
        if (pos == -1) return "application/octet-stream";
        
        pos += 13;
        while (pos < headers.length() && headers.charAt(pos) == ' ') {
            pos++;
        }
        
        int end = headers.indexOf("\r\n", pos);
        if (end == -1) end = headers.indexOf("\n", pos);
        if (end == -1) end = headers.length();
        
        return headers.substring(pos, end).trim();
    }
    
    private static String extractFieldName(String headers) {
        int pos = headers.indexOf("name=\"");
        if (pos == -1) {
            pos = headers.indexOf("name=");
            if (pos == -1) return "file";
            pos += 5;
        } else {
            pos += 6;
        }
        
        int end = headers.indexOf("\"", pos);
        if (end == -1) {
            end = headers.indexOf("\r\n", pos);
            if (end == -1) end = headers.indexOf("\n", pos);
            if (end == -1) end = headers.length();
        }
        
        return headers.substring(pos, end);
    }
    
    private static int indexOf(byte[] array, byte[] pattern, int fromIndex) {
        for (int i = fromIndex; i <= array.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (array[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }
}

