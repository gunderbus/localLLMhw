package cole.oen.cpoels.coekl.AIS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeminiIO implements AIIO {
    private final List<String> loggedData = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String projectId;
    private final String location;
    private final String modelName;

    public GeminiIO() {
        this.projectId = readEnv("GOOGLE_CLOUD_PROJECT", "GCP_PROJECT", "VERTEX_PROJECT");
        this.location = readEnv("GOOGLE_CLOUD_LOCATION", "GCP_LOCATION", "VERTEX_LOCATION");
        String model = readEnv("GEMINI_MODEL", "VERTEX_MODEL");
        this.modelName = model == null || model.isBlank() ? "gemini-1.5-pro" : model;
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("Missing GCP project id. Set GOOGLE_CLOUD_PROJECT.");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Missing GCP location. Set GOOGLE_CLOUD_LOCATION (e.g., us-central1).");
        }
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
        System.out.println("Logged " + loggedData.size() + " file change entries.");
    }

    @Override
    public void handleErrors(Exception e) {
        String message = buildErrorMessage(e);
        System.err.println(message);
        e.printStackTrace(System.err);
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
        return callGemini(prompt);
    }

    @Override
    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths) {
        validatePlanInputs("apply", filePaths);
        String[] contents = readFiles(filePaths);
        String prompt = buildApplyPrompt(transformationPlan, filePaths, contents);
        String json = callGemini(prompt);
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

    public List<String> getLoggedData() {
        return Collections.unmodifiableList(loggedData);
    }

    private String callGemini(String prompt) {
        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            GenerativeModel model = new GenerativeModel(modelName, vertexAI);
            GenerateContentResponse response = model.generateContent(prompt);
            return ResponseHandler.getText(response);
        } catch (IOException e) {
            handleErrors(e);
            throw new RuntimeException("Failed to call Vertex AI Gemini model", e);
        }
    }

    private String readFileSafely(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
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

    private String[] parseTransformedFiles(String json, String[] filePaths) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode filesNode = root.get("files");
            if (filesNode == null || !filesNode.isArray()) {
                throw new RuntimeException("Invalid JSON response: missing files array.");
            }
            java.util.Map<String, String> byPath = new java.util.HashMap<>();
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
            throw new RuntimeException("Failed to parse JSON response from Gemini", e);
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

    private String readEnv(String... keys) {
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String buildErrorMessage(Exception e) {
        Throwable root = rootCause(e);
        String base = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        String normalized = base.toUpperCase();
        if (normalized.contains("PERMISSION_DENIED") || normalized.contains("UNAUTHENTICATED")) {
            return "Gemini auth error: check credentials and project access. Details: " + base;
        }
        if (normalized.contains("RESOURCE_EXHAUSTED") || normalized.contains("QUOTA")) {
            return "Gemini quota error: request limits exceeded. Details: " + base;
        }
        if (normalized.contains("NOT_FOUND") || normalized.contains("MODEL")) {
            return "Gemini model/config error: verify model name, project, and region. Details: " + base;
        }
        if (normalized.contains("TIMEOUT") || normalized.contains("UNAVAILABLE")) {
            return "Gemini network error: service unavailable or timed out. Details: " + base;
        }
        return "Gemini error: " + base;
    }

    private Throwable rootCause(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
    
}
