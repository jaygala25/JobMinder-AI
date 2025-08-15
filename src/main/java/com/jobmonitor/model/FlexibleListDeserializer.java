package com.jobmonitor.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FlexibleListDeserializer extends JsonDeserializer<List<String>> {
    
    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        if (node.isArray()) {
            // Handle array response
            List<String> result = new ArrayList<>();
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    result.add(element.asText());
                } else if (element.isObject()) {
                    // If it's an object, try to extract a meaningful string representation
                    result.add(element.toString());
                }
            }
            return result;
        } else if (node.isObject()) {
            // Handle object response - convert to string representation
            List<String> result = new ArrayList<>();
            result.add(node.toString());
            return result;
        } else if (node.isTextual()) {
            // Handle single string response
            List<String> result = new ArrayList<>();
            result.add(node.asText());
            return result;
        } else if (node.isNull()) {
            // Handle null response
            return new ArrayList<>();
        }
        
        // Default fallback
        return new ArrayList<>();
    }
}
