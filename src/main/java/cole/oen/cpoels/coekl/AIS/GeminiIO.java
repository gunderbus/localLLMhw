package cole.oen.cpoels.coekl.AIS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GeminiIO implements AIIO {
    // Gemini integration TODO: wire Vertex AI client here when ready.

    @Override
    public String processFilesWithAI(String[] filePaths, String aiInstruction) {
        // Placeholder for Gemini API integration.
        return "Transformed file contents based on AI instruction.";
    }

    @Override
    public void saveTransformedFiles(String[] transformedFileContents, String[] originalFilePaths) {
        // Placeholder for saving transformed files
    }

    @Override
    public void logTransformationDetails(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents) {
        // Placeholder for logging transformation details
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
        StringBuilder builder = new StringBuilder();
        builder.append("Planned steps:\n");
        builder.append("1. Review instruction: ").append(aiInstruction).append("\n");
        int step = 2;
        for (int i = 0; i < filePaths.length; i++) {
            String content = contents[i] == null ? "" : contents[i];
            builder.append(step++)
                .append(". Update ")
                .append(filePaths[i])
                .append(" (")
                .append(countLines(content))
                .append(" lines, ")
                .append(content.length())
                .append(" chars)\n");
        }
        builder.append(step).append(". Verify changes and summarize updates.");
        return builder.toString();
    }

    @Override
    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths) {
        // Placeholder for applying transformation plan to files
        return new String[]{"Transformed file content 1", "Transformed file content 2"};
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
