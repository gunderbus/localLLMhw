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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class OllamaIO implements AIIO {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_FILE_CHARS = 160_000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;

    public OllamaIO(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String processFilesWithAI(String[] filePaths, String aiInstruction) {
        String plan = getTransformationPlan(aiInstruction, filePaths);
        String[] transformed = applyTransformationPlan(plan, filePaths);
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
        System.out.println("AI instruction: " + aiInstruction);
        System.out.println("Files: " + Arrays.toString(originalFilePaths));
        System.out.println("Transformed " + transformedFileContents.length + " files.");
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
        String[] contents = readFiles(filePaths);
        String prompt = buildPlanPrompt(aiInstruction, filePaths, contents);
        return callOllama(prompt);
    }

    @Override
    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths) {
        String[] contents = readFiles(filePaths);
        String prompt = buildApplyPrompt(transformationPlan, filePaths, contents);
        String json = callOllama(prompt);
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

    private String callOllama(String prompt) {
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
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode text = json.get("response");
            if (text == null) {
                throw new RuntimeException("Missing response field in Ollama reply.");
            }
            return text.asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to call Ollama", e);
        }
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
            throw new RuntimeException("Failed to parse JSON response from Ollama", e);
        }
    }
}
