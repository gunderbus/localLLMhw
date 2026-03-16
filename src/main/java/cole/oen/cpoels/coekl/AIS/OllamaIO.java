package cole.oen.cpoels.coekl.AIS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OllamaIO implements AIIO {
    // Keep requests bounded so UI doesn't hang forever.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    // Avoid overloading the prompt with extremely large files.
    private static final int MAX_FILE_CHARS = 160_000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final List<String> loggedData = new ArrayList<>();

    public OllamaIO(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        // Shared HTTP client for reuse and connection pooling.
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String processFilesWithAI(String[] filePaths, String aiInstruction) {
        // High-level flow: plan -> apply -> write -> summarize.
        String plan = getTransformationPlan(aiInstruction, filePaths);
        String[] transformed = applyTransformationPlan(plan, filePaths);
        logTransformationDetails(aiInstruction, filePaths, transformed);
        writeFiles(transformed, filePaths);
        showTransformationSummary(aiInstruction, filePaths, transformed);
        return plan;
    }

    @Override
    public void saveTransformedFiles(String[] transformedFileContents, String[] originalFilePaths) {
        writeFiles(transformedFileContents, originalFilePaths);
    }

    @Override
    public void logTransformationDetails(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents) {
        loggedData.clear();
        String[] originals = readFiles(originalFilePaths);
        for (int i = 0; i < originalFilePaths.length; i++) {
            String path = originalFilePaths[i];
            String original = safeGet(originals, i);
            String transformed = safeGet(transformedFileContents, i);
            loggedData.add(buildChangeEntry(path, original, transformed));
        }
        // Simple console logging for now; swap in a logger if needed.
        System.out.println("AI instruction: " + aiInstruction);
        System.out.println("Files: " + Arrays.toString(originalFilePaths));
        System.out.println("Logged " + loggedData.size() + " file change entries.");
    }

    @Override
    public void handleErrors(Exception e) {
        System.err.println("AI backend error: " + e.getMessage());
    }

    @Override
    public void displayStatus(String message) {
        System.out.println(message);
    }

    @Override
    public String getTransformationPlan(String aiInstruction, String[] filePaths) {
        validatePlanInputs(aiInstruction, filePaths);
        // Build a prompt that asks the model for a concise edit plan.
        String[] contents = readFiles(filePaths);
        String prompt = buildPlanPrompt(aiInstruction, filePaths, contents);
        return callOllama(prompt);
    }

    @Override
    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths) {
        // Ask the model to apply the plan and return JSON with new contents.
        String[] contents = readFiles(filePaths);
        String prompt = buildApplyPrompt(transformationPlan, filePaths, contents);
        String json = callOllama(prompt);
        return parseTransformedFiles(json, filePaths);
    }

    @Override
    public String[] readFiles(String[] filePaths) {
        // Read each file from disk into memory.
        String[] contents = new String[filePaths.length];
        for (int i = 0; i < filePaths.length; i++) {
            contents[i] = readFileSafely(filePaths[i]);
        }
        return contents;
    }

    @Override
    public void writeFiles(String[] fileContents, String[] filePaths) {
        // Write each transformed content back to its original path.
        for (int i = 0; i < filePaths.length; i++) {
            writeFileSafely(filePaths[i], fileContents[i]);
        }
    }

    @Override
    public void showTransformationSummary(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents) {
        System.out.println("Applied instruction: " + aiInstruction);
        System.out.println("Updated " + transformedFileContents.length + " files.");
    }

    public List<String> getLoggedData() {
        return Collections.unmodifiableList(loggedData);
    }

    @Override
    public String getAIResponse(String aiInstruction) {
        // For testing: return a canned response instead of calling Ollama.
        return callOllama(aiInstruction);
    }

    private String readFileSafely(String path) {
        try {
            String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            if (content.length() > MAX_FILE_CHARS) {
                // Truncate to keep prompts reasonable while signaling truncation.
                return content.substring(0, MAX_FILE_CHARS) + "\n/* TRUNCATED */";
            }
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }

    private void writeFileSafely(String path, String content) {
        try {
            Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + path, e);
        }
    }

    private String safeGet(String[] values, int index) {
        if (values == null || index < 0 || index >= values.length || values[index] == null) {
            return "";
        }
        return values[index];
    }

    private String buildChangeEntry(String path, String original, String transformed) {
        if (original.equals(transformed)) {
            return "NO_CHANGE: " + path;
        }
        return "CHANGED: " + path
            + " (lines " + countLines(original) + " -> " + countLines(transformed)
            + ", chars " + original.length() + " -> " + transformed.length() + ")";
    }

    private int countLines(String content) {
        if (content.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private String buildPlanPrompt(String instruction, String[] filePaths, String[] contents) {
        // Plan prompt: ask for a short numbered list of edit steps.
        StringBuilder builder = new StringBuilder();
        builder.append("You are planning edits to source files. Provide a concise plan.\n");
        builder.append("Instruction:\n").append(instruction).append("\n\n");
        for (int i = 0; i < filePaths.length; i++) {
            builder.append("File: ").append(filePaths[i]).append("\n");
            builder.append("Content:\n").append(contents[i]).append("\n\n");
        }
        builder.append("Return a short numbered list of steps.");
        return builder.toString();
    }

    private String buildApplyPrompt(String plan, String[] filePaths, String[] contents) {
        // Apply prompt: force JSON-only output for deterministic parsing.
        StringBuilder builder = new StringBuilder();
        builder.append("You are editing files. Apply the plan and instruction.\n");
        builder.append("Return ONLY valid JSON matching this schema:\n");
        builder.append("{\"files\":[{\"path\":\"...\",\"content\":\"...\"}]}\n");
        builder.append("Plan:\n").append(plan).append("\n\n");
        for (int i = 0; i < filePaths.length; i++) {
            builder.append("File: ").append(filePaths[i]).append("\n");
            builder.append("Content:\n").append(contents[i]).append("\n\n");
        }
        builder.append("Remember: return JSON only, no extra text.");
        return builder.toString();
    }

    private String callOllama(String prompt) {
        // Minimal payload compatible with Ollama's /api/generate.
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("stream", false);

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build request JSON", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/generate"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("Ollama error " + response.statusCode() + ": " + response.body());
            }
            // Expected response field is "response" with plain text.
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode text = json.get("response");
            if (text == null) {
                throw new RuntimeException("Missing response field in Ollama reply.");
            }
            return text.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ollama call interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Ollama", e);
        }
    }

    private void validatePlanInputs(String aiInstruction, String[] filePaths) {
        if (aiInstruction == null || aiInstruction.isBlank()) {
            throw new IllegalArgumentException("AI instruction must be provided.");
        }
        if (filePaths == null || filePaths.length == 0) {
            throw new IllegalArgumentException("At least one file path must be provided.");
        }
        for (String path : filePaths) {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("File path cannot be blank.");
            }
        }
    }

    private String[] parseTransformedFiles(String json, String[] filePaths) {
        // Parse JSON into a path->content map, then align to original order.
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode filesNode = root.get("files");
            if (filesNode == null || !filesNode.isArray()) {
                throw new RuntimeException("Invalid JSON response: missing files array.");
            }
            Map<String, String> byPath = new HashMap<>();
            for (JsonNode node : filesNode) {
                JsonNode path = node.get("path");
                JsonNode content = node.get("content");
                if (path != null && content != null) {
                    byPath.put(path.asText(), content.asText());
                }
            }
            String[] output = new String[filePaths.length];
            for (int i = 0; i < filePaths.length; i++) {
                String path = filePaths[i];
                output[i] = byPath.getOrDefault(path, readFileSafely(path));
            }
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON response from Ollama", e);
        }
    }
}
