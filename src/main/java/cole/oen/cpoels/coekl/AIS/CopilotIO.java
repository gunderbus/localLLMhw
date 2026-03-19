package cole.oen.cpoels.coekl.AIS;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CopilotIO implements AIIO {
    private static final int MAX_FILE_CHARS = 160_000;
    private static final String DEFAULT_BASE_URL = "";
    private static final String DEFAULT_MODEL = "";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiToken;
    private final String baseUrl;
    private final String model;
    private final List<String> loggedData = new ArrayList<>();

    public CopilotIO() {
        this(
            System.getenv("GITHUB_TOKEN"),
            DEFAULT_BASE_URL,
            DEFAULT_MODEL
        );
    }

    public CopilotIO(String apiToken, String baseUrl, String model) {
        this.apiToken = apiToken;
        this.baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl.trim();
        this.model = model == null ? DEFAULT_MODEL : model.trim();
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
        System.err.println("Copilot backend error: " + e.getMessage());
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
        return callCopilot(prompt);
    }

    @Override
    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths) {
        validatePlanInputs("apply", filePaths);
        String[] contents = readFiles(filePaths);
        String prompt = buildApplyPrompt(transformationPlan, filePaths, contents);
        callCopilot(prompt);
        return contents;
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
        return callCopilot(aiInstruction);
    }

    public List<String> getLoggedData() {
        return Collections.unmodifiableList(loggedData);
    }

    private String callCopilot(String prompt) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException("Missing GITHUB_TOKEN for Copilot backend.");
        }
        throw new UnsupportedOperationException(
            "CopilotIO is initialized but the Copilot API request is not wired yet. "
                + "Use httpClient/objectMapper/baseUrl/model inside callCopilot()."
        );
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
