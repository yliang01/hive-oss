package cc.cc3c.hive.oss.controller;

public record PreviewDecision(boolean previewable, String mimeType, String blockedReason) {

    public static PreviewDecision allowed(String mimeType) {
        return new PreviewDecision(true, mimeType, null);
    }

    public static PreviewDecision blocked(String blockedReason) {
        return new PreviewDecision(false, null, blockedReason);
    }
}
