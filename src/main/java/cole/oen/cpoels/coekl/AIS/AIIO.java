package cole.oen.cpoels.coekl.AIS;

public interface AIIO {

    // Interface ideas
    // Maybe instead could contact different ais?
    public String processFilesWithAI(String[] filePaths, String aiInstruction);

    public void saveTransformedFiles(String[] transformedFileContents, String[] originalFilePaths);

    public void logTransformationDetails(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents);

    public void handleErrors(Exception e);

    public void displayStatus(String message);

    public String[] getTransformationPlan(String aiInstruction, String[] filePaths);

    public String[] applyTransformationPlan(String transformationPlan, String[] filePaths);

    public String[] readFiles(String[] filePaths);

    public void writeFiles(String[] fileContents, String[] filePaths);

    public void showTransformationSummary(String aiInstruction, String[] originalFilePaths, String[] transformedFileContents);
    
}
