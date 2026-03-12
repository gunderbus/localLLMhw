package cole.oen.cpoels.coekl.AIS;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

public class GeminiIO implements AIIO {
    // Implementation of the gemini model
    new VertexAI client = VertexAI.createClient();
    GenerativeModel model = client.getGenerativeModel("gemini-1.5-pro");

    @Override
    public String processFilesWithAI(String[] filePaths, String aiInstruction) {
        // Placeholder for Gemini API integration
        
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
        // Placeholder for displaying status messages
    }

    @Override
    public String getTransformationPlan(String aiInstruction, String[] filePaths) {
        // Placeholder for getting transformation plan from Gemini API
        model.generateContent("Create a concise transformation plan for the following instruction: " + aiInstruction + " based on the contents of files: " + readFiles(filePaths), new ResponseHandler<GenerateContentResponse>() {
            @Override
            public void onResponse(GenerateContentResponse response) {
                // Handle successful response
                String plan = response.getContent();
                System.out.println("Received transformation plan: " + plan);
                return plan;
            }

            @Override
            public void onError(Exception e) {
                // Handle error
                handleErrors(e);
            }
        });
        return "Unfortunatley there was an error. Please try again.";
    }

    @Override
    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths) {
        // Placeholder for applying transformation plan to files
        return new String[]{"Transformed file content 1", "Transformed file content 2"};
    }

    @Override
    public String[] readFiles(String[] filePaths) {
        var contents = new String[filePaths.length];
        for(String filePath : filePaths) {
            // Placeholder for reading file contents
            model.generateContent("Read contents of " + filePath + "... PLEASE BE SUPER SPECIFIC", new ResponseHandler<GenerateContentResponse>() {
                @Override
                public void onResponse(GenerateContentResponse response) {
                    // Handle successful response
                    contents.[filePath] = response.getContent();
                    return contents;
                }

                @Override
                public void onError(Exception e) {
                    // Handle error
                    handleErrors(e);
                }
            });
        }
        return "Unfortunatley there was an error. Please try again.";
    }   

    @Override
    public void writeFiles(String[] fileContents, String[] filePaths) {
        // Placeholder for writing files
    }

    @Override
    public void showTransformationSummary(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents) {
        // Placeholder for showing transformation summary to the user
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
