/**
 * AtlasUI: the JavaFX widget and theming framework that hosts (GitAlpha)
 * consume as a jar.
 * <p>
 * Exported packages, by layer: {@code com.atlasui.Type} holds the framework
 * types (the {@code com.atlasui.Type.ISerializable} persistence contract, the
 * typed color model and the variant enums); {@code com.atlasui.Theme} holds the
 * palette model, {@code ThemeManager} and the base {@code ThemeSkin} machinery,
 * with concrete palettes in {@code com.atlasui.Theme.Themes} and one skin per
 * themed element in {@code com.atlasui.Theme.Skin}; and
 * {@code com.atlasui.UI.Components} holds the A-prefixed JavaFX controls
 * (buttons, lists, split pane, tab panel, …). The framework never imports host
 * code — host settings reach it through setters and static defaults.
 * <p>
 * {@code com.atlasui.UI.Animation} holds the frame-time plumbing
 * ({@code AAnimationTimer} and {@code ITimeStepEvent}) that turns JavaFX's
 * nanosecond pulse stamp into a per-frame {@code double} time step in
 * seconds.
 * <p>
 * {@code org.json} is a transitive requirement because exported signatures
 * expose {@code JSONObject}: {@code ISerializable.Serialize/Deserialize/
 * OnSerialize/OnDeserialize} take or return it, so any module reading these
 * exports must be able to read {@code org.json} as well.
 */
module com.atlasui
{
    // transitive: exported palette/ISerializable signatures expose JSONObject,
    // so modules reading AtlasUI's exports must also read org.json.
    requires transitive org.json;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;

    exports com.atlasui.Type;
    exports com.atlasui.Theme;
    exports com.atlasui.Theme.Skin;
    exports com.atlasui.Theme.Themes;
    exports com.atlasui.UI.Animation;
    exports com.atlasui.UI.Components;

    opens com.atlasui.UI.Components to javafx.graphics;
}
