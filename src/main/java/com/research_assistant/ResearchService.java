package com.research_assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class ResearchService {

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ResearchService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String processContent(ResearchRequest request) {
        try {
            // Build the prompt based on the requested operation and content
            String prompt = buildPrompt(request);

            // Create the request body for the API call
            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[] {
                            Map.of("parts", new Object[] { Map.of("text", prompt) })
                    }
            );

            // Construct the full URL with the API key added to the URL as a query parameter
            String fullUrl = geminiApiUrl + geminiApiKey;

            // Make the POST request to the Gemini API
            String response = webClient.post()
                    .uri(fullUrl) // Full URL with the geminiApiKey already appended
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse the response and extract the relevant text
            return extractTextFromResponse(response);
        } catch (WebClientResponseException e) {
            // Handle WebClient response errors (e.g., 400, 500)
            return "API Error: " + e.getStatusCode() + " - " + e.getMessage();
        } catch (Exception e) {
            // Handle other exceptions (e.g., network errors)
            e.printStackTrace();
            return "Internal Error: " + e.getMessage();
        }
    }

    private String extractTextFromResponse(String response) {
        try {
            // Deserialize the response into a GeminiResponse object
            GeminiResponse geminiResponse = objectMapper.readValue(response, GeminiResponse.class);
            if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
                GeminiResponse.Candidate firstCandidate = geminiResponse.getCandidates().get(0);
                if (firstCandidate.getContent() != null && firstCandidate.getContent().getParts() != null && !firstCandidate.getContent().getParts().isEmpty()) {
                    return firstCandidate.getContent().getParts().get(0).getText();
                }
            }
            return "No content found";
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage();
        }
    }

    private String buildPrompt(ResearchRequest request) {
        StringBuilder prompt = new StringBuilder();

        // Build the prompt based on the operation
        switch (request.getOperation()) {
            case "summarize":
                prompt.append("Summarize the following text: ");
                break;
            case "suggest":
                prompt.append("Based on the following text, suggest a related topic: ");
                break;
            case "analyze":
                prompt.append("Analyze the following text: ");
                break;
            default:
                throw new IllegalArgumentException("Invalid operation: " + request.getOperation());
        }

        // Append the content to the prompt
        prompt.append(request.getContent());
        return prompt.toString();
    }
}
