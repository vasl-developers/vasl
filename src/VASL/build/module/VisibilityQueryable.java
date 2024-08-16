package VASL.build.module;

public interface VisibilityQueryable {
    boolean getShadingVisible();
    String getShadingLevel();
    void setStateFromSavedGame(Boolean v, String s);
}
