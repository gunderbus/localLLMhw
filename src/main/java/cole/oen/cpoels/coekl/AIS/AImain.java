package cole.oen.cpoels.coekl.AIS;

public class AImain {
    private AIIO AIinterface;

    public AImain(AIIO init){
        AIinterface = init;
    }

    TransformationResult executeTransformation(String[] filePaths, String aiInstruction) {
        try {
            AIinterface.displayStatus("Reading files...");
            String[] fileContents = AIinterface.readFiles(filePaths);

            AIinterface.displayStatus("Generating transformation plan...");
            String transformationPlan = AIinterface.getTransformationPlan(aiInstruction, filePaths);

            AIinterface.displayStatus("Applying transformation plan...");
            String[] transformedFileContents = AIinterface.applyTransformationPlan(transformationPlan, filePaths);
            AIinterface.displayStatus("Saving transformed files...");
            AIinterface.writeFiles(transformedFileContents, filePaths);
            AIinterface.logTransformationDetails(aiInstruction, filePaths, transformedFileContents);
            AIinterface.showTransformationSummary(aiInstruction, filePaths, transformedFileContents);
            return new TransformationResult(true, "Transformation successful.");
        } catch (Exception e) {
            AIinterface.handleErrors(e);
            return new TransformationResult(false, "Transformation failed: " + e.getMessage());
        }
    }

}
