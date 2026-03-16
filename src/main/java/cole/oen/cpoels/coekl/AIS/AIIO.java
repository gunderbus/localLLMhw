package cole.oen.cpoels.coekl.AIS;

public interface AIIO {
    String processFilesWithAI(String[] filePaths, String aiInstruction);

    void saveTransformedFiles(String[] transformedFileContents, String[] originalFilePaths);

    void logTransformationDetails(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents);

    void handleErrors(Exception e);

    void displayStatus(String message);

    String getTransformationPlan(String aiInstruction, String[] filePaths);

    String[] applyTransformationPlan(String transformationPlan, String[] filePaths);

    String[] readFiles(String[] filePaths);

    void writeFiles(String[] fileContents, String[] filePaths);

    void showTransformationSummary(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents);

    String getAIResponse(String aiInstruction);
}
