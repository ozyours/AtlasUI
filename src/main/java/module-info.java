/**
 * AtlasUI: the JavaFX widget and theming framework that hosts (GitAlpha)
 * consume as a jar.
 * <p>
 * Exported packages, by layer: {@code com.atlasui.type} holds the framework
 * types (the {@code com.atlasui.type.ISerializable} persistence contract, the
 * typed color model and the variant enums); {@code com.atlasui.theme} holds the
 * palette model, {@code ThemeManager} and the base {@code ThemeSkin} machinery,
 * with concrete palettes in {@code com.atlasui.theme.themes} and one skin per
 * themed element in {@code com.atlasui.theme.skin}; and
 * {@code com.atlasui.ui.components} holds the A-prefixed JavaFX controls
 * (buttons, lists, split pane, tab panel, …). The framework never imports host
 * code — host settings reach it through setters and static defaults.
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

    exports com.atlasui.type;
    exports com.atlasui.theme;
    exports com.atlasui.theme.skin;
    exports com.atlasui.theme.themes;
    exports com.atlasui.ui.components;

    opens com.atlasui.ui.components to javafx.graphics;
}
