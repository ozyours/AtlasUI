package com.atlasui.Theme.Skin;

import com.atlasui.Theme.ColorPalette;
import com.atlasui.Type.ETabButtonVariant;
import com.atlasui.Type.ThemeColor;

import java.util.Map;

/**
 * Tab style for the tab faces of {@link com.atlasui.UI.Components.ATabWidget}
 * (and the {@code ×} close {@link com.atlasui.UI.Components.ATabButton} they
 * host): the buttons render as flat tabs — square corners, generous
 * {@code em} padding and a larger label — sitting on the window backdrop
 * (Background 2), while the tab faces themselves derive from the panel
 * background (Background 1): inactive tabs darker (-12%), hover warmer
 * (-6%), the selected tab brighter (+12%) and labelled in the body text
 * colour (unselected in muted), with a pressed shade and content hairline,
 * rendering the button header visually interchangeable with a real
 * {@code TabPane} header. A face being dragged by the reorder gesture is
 * brighter still (+18%) and carries a drop shadow, which together with the
 * closed-hand cursor reads as the face being physically lifted over the
 * strip. No focus ring is drawn — tab buttons are navigation controls, not
 * primary actions — so the {@code .a-tab-button:focused} rule re-asserts the
 * flat, zero-inset background: Modena paints its ring as an extra background
 * layer, which would otherwise reappear the moment a tab takes focus. Faces
 * also pack their content to the left — title flush left, close glyph 2 px
 * to its right — so a face narrower than its maximum width still reads as a
 * tab rather than a label floating in the middle of the strip. Which side the
 * glyph lands on is pinned with {@code -fx-content-display: right} because
 * Modena's {@code .button} default lays the graphic out before its text,
 * which would place the {@code ×} to the left of the title.
 * <p>
 * The skin has two size variants ({@link ETabButtonVariant}): {@code NORMAL}
 * is the full-size strip (main project tabs), {@code SMALL} shrinks the face
 * padding and label for secondary strips (the "Changes" / "History"
 * sub-tabs). Both variants share one CSS template; only the metrics differ,
 * so colors stay in one place and variants can never drift apart in hue.
 * <p>
 * The {@code .a-tab-close} rules style the {@code ×} close
 * {@link com.atlasui.UI.Components.ATabButton} that a closable tab face hosts
 * as its graphic in modifiable mode: the Modena button chrome (background and
 * border, with their insets) is reset to transparent so it reads as a flat
 * face — muted normally, primary on hover as the affordance cue; the hover
 * rule re-asserts the transparent background so only the label colour changes.
 * Because the close button also carries {@code .a-tab-button}, whose
 * {@code :hover}/{@code :pressed} rules would otherwise out-specify the flat
 * reset, the compound {@code .a-tab-button.a-tab-close} rules at the end
 * re-flatten those states. The {@code ×} glyph inherits the tab face's font
 * size, so it scales with the variant.
 * <p>
 * Thirteen {@code %s} placeholders in CSS order: header strip Background2,
 * inactive tab derive -12%, inactive label muted, hover tab derive -6%,
 * selected tab derive +12%, selected label text, pressed shade, dragged tab
 * derive +18% (with the drop shadow and closed-hand cursor of the lifted
 * face), content hairline border, close face muted, close face hover, then
 * muted and hover repeated for the compound close-button state rules. Shades
 * are computed in CSS with {@code derive()} so the skin needs only palette
 * hex values plus derived expressions.
 * <p>
 * The tab header's horizontal scroll bar is suppressed by setting
 * {@link javafx.scene.control.ScrollPane.ScrollBarPolicy#NEVER} on its
 * {@link javafx.scene.control.ScrollPane} in
 * {@link com.atlasui.UI.Components.ATabWidget}, rather than hiding a
 * post-attach skin node with CSS; programmatic and wheel scrolling remain
 * available.
 */
public final class TabButtonSkin extends ThemeSkin
{
	/**
	 * CSS template with three metric tokens ({@code @PAD@}, {@code @FONT@},
	 * {@code @GAP@}) resolved per {@link ETabButtonVariant} in the
	 * constructor, plus thirteen {@code %s} color placeholders in CSS order:
	 * header strip Background2, inactive tab, inactive label, hover tab,
	 * selected tab, selected label, pressed shade, dragged tab, content
	 * hairline, close face normal, close face hover, then muted and hover
	 * repeated for the compound {@code .a-tab-button.a-tab-close} state rules
	 * — as consumed by
	 * {@link #GetColorArguments}. The {@code :focused} rule carries no colour
	 * placeholder: it only re-asserts the flat background, because the focus
	 * ring Modena would draw there is not wanted on a tab. The
	 * {@code .a-tab-button} rule also pins the face layout: content packed
	 * left ({@code center-left}) with the hosted {@code ×} graphic set to
	 * {@code -fx-content-display: right} and a 2 px
	 * {@code -fx-graphic-text-gap}, so the title sits flush left and the glyph
	 * trails it; Modena's {@code .button} default draws the graphic first and
	 * would otherwise put the {@code ×} before the title. The tab header's
	 * horizontal bar is omitted by setting
	 * {@link javafx.scene.control.ScrollPane.ScrollBarPolicy#NEVER} on the pane
	 * in {@link com.atlasui.UI.Components.ATabWidget}; this skin does not need
	 * CSS rules to hide a mounted bar.
	 */
	private static final String CSS_TEMPLATE = """
			.a-tab-header {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			}
			.a-tab-button {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			    -fx-border-color: transparent;
			    -fx-border-insets: 0;
			    -fx-border-radius: 0;
			    -fx-text-fill: %s;
			    -fx-padding: @PAD@;
			    -fx-font-size: @FONT@;
			    -fx-cursor: hand;
			    -fx-alignment: center-left;
			    -fx-content-display: right;
			    -fx-graphic-text-gap: 2;
			}
			.a-tab-button:focused {
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			}
			.a-tab-button:hover {
			    -fx-background-color: %s;
			}
			.a-tab-button:selected {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			    -fx-text-fill: %s;
			}
			.a-tab-button:pressed {
			    -fx-background-color: %s;
			}
			.a-tab-button:dragging {
			    -fx-background-color: %s;
			    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.35), 10, 0.15, 0, 3);
			    -fx-cursor: closed_hand;
			}
			.a-tab-content {
			    -fx-border-color: %s transparent transparent transparent;
			    -fx-border-width: 1 0 0 0;
			}
			.a-tab-close {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-border-color: transparent;
			    -fx-border-insets: 0;
			    -fx-text-fill: %s;
			    -fx-padding: 0 0 0 @GAP@;
			    -fx-cursor: hand;
			}
			.a-tab-close:hover {
			    -fx-background-color: transparent;
			    -fx-text-fill: %s;
			}
			.a-tab-button.a-tab-close {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-border-color: transparent;
			    -fx-border-insets: 0;
			    -fx-text-fill: %s;
			    -fx-padding: 0 0 0 @GAP@;
			    -fx-cursor: hand;
			}
			.a-tab-button.a-tab-close:hover {
			    -fx-background-color: transparent;
			    -fx-text-fill: %s;
			}
			.a-tab-button.a-tab-close:pressed {
			    -fx-background-color: transparent;
			}
			""";

	/** The variant this instance bakes; fixed at construction */
	private final ETabButtonVariant Variant;

	/**
	 * The variant-resolved format: {@link #CSS_TEMPLATE} with the metric
	 * tokens replaced. Still carries the thirteen {@code %s} color
	 * placeholders (the same count and order as
	 * {@code GetColorArguments} supplies) for {@link #GetCssFormat}.
	 */
	private final String CssFormat;

	/**
	 * @param _Variant the size variant to bake (NORMAL = full-size strip,
	 *                 SMALL = compact secondary strip)
	 */
	public TabButtonSkin(ETabButtonVariant _Variant)
	{
		Variant = _Variant;

		String __Pad;
		String __Font;
		String __Gap;
		switch (_Variant)
		{
			case SMALL ->
			{
				__Pad = "0.12em 0.65em 0.11em 0.65em";
				__Font = "1.05em";
				__Gap = "3";
			}
			default ->
			{
				__Pad = "0.28em 1.0em 0.27em 1.0em";
				__Font = "1.2em";
				__Gap = "4";
			}
		}

		CssFormat = CSS_TEMPLATE
				.replace("@PAD@", __Pad)
				.replace("@FONT@", __Font)
				.replace("@GAP@", __Gap);
	}

	/**
	 * @return the variant-resolved tab-button CSS format
	 *         ({@code #CssFormat})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CssFormat;
	}

	/**
	 * Resolve the thirteen {@code %s} placeholders of {@link #CssFormat} in
	 * CSS order: header strip Background2, inactive tab derive -12% from
	 * Background, inactive label muted, hover tab derive -6%, selected tab
	 * derive +12%, selected label text, pressed shade, dragged tab derive
	 * +18%, content hairline border, close face muted, close face hover, then
	 * muted and hover repeated for the compound close-button state rules.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the thirteen placeholder values in CSS order
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Bg = _Palette.GetBackgroundColor().GetHex(__Lookup);
		return new Object[] {
				_Palette.GetBackground2Color().GetHex(__Lookup),
				"derive(" + __Bg + ", -12%)",
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				"derive(" + __Bg + ", -6%)",
				"derive(" + __Bg + ", +12%)",
				_Palette.GetTextColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				"derive(" + __Bg + ", +18%)",
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetPrimaryColor().GetHex(__Lookup),
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetPrimaryColor().GetHex(__Lookup) };
	}
}
