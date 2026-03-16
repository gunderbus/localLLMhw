package cole.oen.cpoels.coekl.AIS;

public class TransformationResult {
    private final boolean success;
    private final String message;

    public TransformationResult(boolean success, String message) {
        this.success = success;
        this.message = message == null ? "" : message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
