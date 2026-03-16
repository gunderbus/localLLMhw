
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

public class ClaudeIO implements AIIO {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_FILE_CHARS = 160_000;
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final String DEFAULT_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String version;
    private final int maxTokens;
    private final List<String> loggedData = new ArrayList<>();

    public ClaudeIO() {
        this(
            System.getenv("ANTHROPIC_API_KEY"),
            DEFAULT_BASE_URL,
            DEFAULT_MODEL,
            DEFAULT_VERSION,
            DEFAULT_MAX_TOKENS
        );
    }

    public ClaudeIO(String apiKey, String baseUrl, String model, String version, int maxTokens) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.version = version == null || version.isBlank() ? DEFAULT_VERSION : version.trim();
        this.maxTokens = maxTokens <= 0 ? DEFAULT_MAX_TOKENS : maxTokens;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String processFilesWithAI(String[] filePaths, String aiInstruction) {
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
        System.out.println("AI instruction: " + aiInstruction);
        System.out.println("Files: " + Arrays.toString(originalFilePaths));
        System.out.println("Logged " + loggedData.size() + " file change entries.");
    }

    @Override
    public void handleErrors(Exception e) {
        System.err.println("Claude backend error: " + e.getMessage());
    }

    @Override
    public void displayStatus(String message) {
        System.out.println(message);
    }

    @Override
    public String getTransformationPlan(String aiInstruction, String[] filePaths) {
        validatePlanInputs(aiInstruction, filePaths);
        String[] contents = readFiles(filePaths);
        String prompt = buildPlanPrompt(aiInstruction, filePaths, contents);
        return callClaudeAPI(prompt);
    }

    @Override
    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths) {
        validatePlanInputs("apply", filePaths);
        String[] contents = readFiles(filePaths);
        String prompt = buildApplyPrompt(transformationPlan, filePaths, contents);
        String json = callClaudeAPI(prompt);
        return parseTransformedFiles(json, filePaths);
    }

    @Override
    public String[] readFiles(String[] filePaths) {
        String[] contents = new String[filePaths.length];
        for (int i = 0; i < filePaths.length; i++) {
            contents[i] = readFileSafely(filePaths[i]);
        }
        return contents;
    }

    @Override
    public void writeFiles(String[] fileContents, String[] filePaths) {
        for (int i = 0; i < filePaths.length; i++) {
            writeFileSafely(filePaths[i], fileContents[i]);
        }
    }

    @Override
    public void showTransformationSummary(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents) {
        System.out.println("Applied instruction: " + aiInstruction);
        System.out.println("Updated " + transformedFileContents.length + " files.");
    }

    @Override
    public String getAIResponse(String aiInstruction) {
        return callClaudeAPI(aiInstruction);
    }

    public String callClaudeAPI(String aiInstruction) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing ANTHROPIC_API_KEY.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", maxTokens);
        payload.put("messages", List.of(Map.of("role", "user", "content", aiInstruction)));

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build request JSON", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", version)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("Claude error " + response.statusCode() + ": " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode content = json.get("content");
            if (content == null || !content.isArray() || content.isEmpty()) {
                throw new RuntimeException("Missing content in Claude reply.");
            }
            JsonNode textNode = content.get(0).get("text");
            if (textNode == null) {
                throw new RuntimeException("Missing text in Claude reply.");
            }
            return textNode.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Claude call interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Claude", e);
        }
    }

    public List<String> getLoggedData() {
        return Collections.unmodifiableList(loggedData);
    }

    private String readFileSafely(String path) {
        try {
            String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            if (content.length() > MAX_FILE_CHARS) {
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

    private String buildPlanPrompt(String instruction, String[] filePaths, String[] contents) {
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

    private String[] parseTransformedFiles(String json, String[] filePaths) {
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
            throw new RuntimeException("Failed to parse JSON response from Claude", e);
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
    
}
