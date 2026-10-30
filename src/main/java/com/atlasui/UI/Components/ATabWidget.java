package com.atlasui.UI.Components;

import com.atlasui.Theme.ColorPalette;
import com.atlasui.Theme.IThemeChangeEvent;
import com.atlasui.Theme.ThemeManager;
import com.atlasui.Type.ETabButtonVariant;
import com.atlasui.UI.Animation.AAnimationTimer;
import com.atlasui.UI.Animation.ITimeStepEvent;
import javafx.geometry.Bounds;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully controllable tab widget: a row of flat tab faces above a
 * {@link StackPane} content swap, optionally user-modifiable — the
 * constructor flag turns on the {@code "+"} affix (new tab), a {@code ×}
 * close button on every tab, and drag-to-reorder of the header faces. When
 * not modifiable, tabs are managed purely programmatically.
 * <p>
 * The header is horizontally scrollable when tab faces overflow: the face
 * strip sits inside a {@link ScrollPane}, while the {@code "+"} affix (in
 * modifiable mode) is pinned to the right edge outside the scroll area.
 * The scroll bar itself never shows — the pane's hbar policy is set to
 * {@link ScrollPane.ScrollBarPolicy#NEVER} so the skin never mounts it —
 * and wheel / trackpad input scrolls the strip horizontally via
 * {@link #InstallWheelScroll()}. Tab face widths are configurable
 * via {@link #SetTabMinWidth}, {@link #SetTabMaxWidth} and
 * {@link #SetTabPrefWidth}; all three apply uniformly to every face
 * including the affix. The maximum width starts at a framework-wide default
 * ({@link #TAB_FACE_MAX_WIDTH_DEFAULT} pixels) that a host overrides once at
 * startup through {@link #SetDefaultTabMaxWidth} — the framework itself never
 * reads application settings.
 * <p>
 * Each tab face is an {@link ATabButton} — a {@link javafx.scene.control.Button}
 * whose text is the tab title and whose graphic is the {@code ×} close glyph
 * — styled entirely by the cascading tab skin through the {@code a-tab-button}
 * / {@code a-tab-close} style classes; selection is a custom {@code :selected}
 * pseudo-class, the lifted state a custom {@code :dragging} one, and the
 * pressed state the button's own armed state. Every face is
 * focus-traversable and activates on primary click, Space or Enter. The
 * {@code "+"} affix is the same component without a close glyph, kept out of
 * the tab list so it can never be selected or reordered. Reorder is live and
 * cursor-driven (browser-tab style): once a drag passes a small threshold the
 * face lifts above its siblings and follows the pointer while the strip
 * permutes underneath it, the lifted face carries the {@code :dragging}
 * pseudo-class the skin renders as a raised tab, neighbouring faces slide into
 * their new slots (each face easing its own offset back to zero through
 * {@link ATabButton#SlideFrom(double)}), and the parallel content list plus
 * the selected index follow the permutation. That slide state lives on the
 * face, not here: the strip only hands each neighbour the offset to ease from
 * and freezes every face's slide before it permutes the row or hands a face to
 * the drag follow.
 * An {@link AAnimationTimer} drives the follow, so a pointer held near a
 * viewport edge keeps auto-scrolling the strip and reordering through tabs
 * that are currently scrolled out of view. The ticker hands the drag a
 * per-frame delta time in seconds, and the auto-scroll speed is expressed as
 * pixels per second from it, so the gesture behaves identically at any
 * display refresh rate. Because a permutation re-parents the pressed face —
 * which costs that face its JavaFX mouse-event binding for the rest of the
 * gesture — the pointer position and the release are also read from the scene
 * while a drag runs ({@link #InstallGestureHandlers()}), so the follow and the
 * drop keep working after the first reorder; a scene-level move that finds the
 * primary button already up ends a gesture whose release was never delivered.
 * The per-face handlers stay the primary path, and the press stays on the face
 * so a press on the close glyph (which consumes its own events) never arms a
 * drag.
 * <p>
 * Tab action handlers resolve their index via {@code TabButtons.indexOf} at
 * invocation time rather than capturing the creation index, so closures stay
 * valid across reorders and closes.
 * <p>
 * One stylesheet (baked by {@code TabButtonSkin} at this widget's
 * {@link ETabButtonVariant}) cascades from this root
 * to all descendants ({@code .a-tab-header}, {@code .a-tab-button},
 * {@code .a-tab-close}, {@code .a-tab-content}), so header strip, tab faces,
 * close buttons and hairline re-bake together on palette switches. Listeners
 * (selection / new-tab request / tab closed / tab order changed) are held as
 * weak references and pruned on broadcast; the widget's own theme registration
 * stays alive as long as the widget is in the scene graph. Only a drag-reorder
 * reports an order change. Consumers read the current order back through
 * {@link #GetTabContents()} and {@link #GetTabHeaders()}, which both return
 * index-aligned copies — re-read them after any reorder or close rather than
 * caching them.
 */
public class ATabWidget extends VBox implements IThemeChangeEvent
{
	/**
	 * Pixels the pointer must travel before a press becomes a drag-reorder
	 */
	private static final double DRAG_THRESHOLD = 6;

	/**
	 * Width (pixels) of the viewport-edge zone that auto-scrolls the strip
	 * during a drag: as soon as the pointer enters this band at either end of
	 * the visible strip, the header starts scrolling so tabs that are out of
	 * view stay reachable.
	 */
	private static final double AUTO_SCROLL_EDGE_PX = 40;

	/**
	 * Auto-scroll speed reached at the very edge of the viewport, tuned as
	 * pixels per frame at {@link #AUTO_SCROLL_REFERENCE_FPS} — so the per-second
	 * speed is this value times that reference rate. Tuning in per-frame units
	 * keeps the number readable, while scaling it by the frame's delta time makes
	 * the strip travel at the same visual speed on a 60 Hz and a 120 Hz display.
	 */
	private static final double AUTO_SCROLL_MAX_PX = 14;

	/**
	 * Refresh rate (frames per second) that {@link #AUTO_SCROLL_MAX_PX} was
	 * tuned at: the per-frame figure only becomes a time-based speed when
	 * multiplied by this, and the product is then scaled by the actual frame
	 * delta. Changing the display's refresh rate therefore changes only how
	 * finely the scroll is stepped, never how fast the strip travels.
	 */
	private static final double AUTO_SCROLL_REFERENCE_FPS = 60;

	/**
	 * Maximum face height (pixels) for the {@link ETabButtonVariant#SMALL}
	 * variant; matches the CSS-computed content height of a SMALL tab button
	 * (title, close glyph and compact padding) so the header strip is visibly
	 * shorter than the NORMAL main-tab strip.
	 */
	private static final double TAB_FACE_SMALL_MAX_HEIGHT = 26;

	/**
	 * Built-in maximum tab face width (pixels): the fallback that keeps a strip
	 * at the historical width when the host never calls
	 * {@link #SetDefaultTabMaxWidth}, and the value the framework-wide default
	 * starts from.
	 */
	private static final double TAB_FACE_MAX_WIDTH_DEFAULT = 150;

	/**
	 * Framework-wide default maximum tab face width (pixels) applied by every
	 * {@link ATabWidget} at construction. Hosts that persist a user preference
	 * set it once at startup through {@link #SetDefaultTabMaxWidth} (GitAlpha
	 * feeds in its {@code AlphaSettings.TabMaxSize} setting), so every strip —
	 * main tabs and SMALL sub-tabs alike — picks up the same value without the
	 * framework knowing about application settings.
	 * <p>
	 * Read only by the constructor: changing it never retunes strips that
	 * already exist, which is what {@link #SetTabMaxWidth} is for.
	 */
	private static double DefaultTabMaxWidth = TAB_FACE_MAX_WIDTH_DEFAULT;

	/**
	 * The outer header bar: carries the {@code .a-tab-header} background
	 * strip and holds the scroll pane (tab faces) plus the pinned
	 * {@code "+"} affix.
	 */
	private final HBox HeaderBar;
	/**
	 * The scroll pane wrapping the inner header. Scrollable horizontally
	 * (its hbar policy is set to {@link ScrollPane.ScrollBarPolicy#NEVER}
	 * so the skin never mounts a bar node), and wheel input is translated
	 * to horizontal scrolling by {@link #InstallWheelScroll()}. Vertical
	 * scroll is always disabled.
	 */
	private final ScrollPane HeaderScrollPane;
	/**
	 * The inner header row holding only the tab faces (no affix)
	 */
	private final HBox Header;
	/**
	 * The content stack: whichever child was last set via setAll is visible
	 */
	private final StackPane Content;
	/**
	 * All tab faces, in current display order
	 */
	private final List<ATabButton> TabButtons;
	/**
	 * All content nodes, parallel to TabButtons
	 */
	private final List<Node> TabContents;
	/**
	 * Whether the user may add, close and reorder tabs (programmatic
	 * {@link #AddTab} is always available)
	 */
	private final boolean Modifiable;
	/**
	 * The tab-button skin size variant baked into this strip's cascading
	 * stylesheet ({@code NORMAL} = full-size main tabs, {@code SMALL} =
	 * compact sub-tabs); fixed at construction
	 */
	private final ETabButtonVariant TabVariant;
	/**
	 * The {@code "+"} affix shown when modifiable; null otherwise. Pinned
	 * to the right edge of the header bar, outside the scroll area.
	 * Deliberately kept out of {@link #TabButtons}, so selection,
	 * reordering and close logic never see it — its click only fires
	 * {@link INewTabRequestEvent}s.
	 */
	private final ATabButton btn_AddTab;
	/**
	 * Configured minimum width for every tab face (default {@link Control#USE_COMPUTED_SIZE})
	 */
	private double TabMinWidth = Control.USE_COMPUTED_SIZE;
	/**
	 * Configured maximum width for every tab face (constructor baseline {@link #TAB_FACE_MAX_WIDTH_DEFAULT}, or the host's {@link #SetDefaultTabMaxWidth}; {@link Control#USE_COMPUTED_SIZE} once reverted)
	 */
	private double TabMaxWidth = Control.USE_COMPUTED_SIZE;
	/**
	 * Configured preferred width for every tab face (default {@link Control#USE_COMPUTED_SIZE})
	 */
	private double TabPrefWidth = Control.USE_COMPUTED_SIZE;
	/**
	 * Scene X of the press that may become a drag-reorder: the origin of the
	 * {@link #DRAG_THRESHOLD} test and the anchor the grab offset is measured
	 * from, so the face does not jump when the threshold is crossed.
	 */
	private double DragStartX;
	/**
	 * Scene Y of that press. Vertical travel counts toward the threshold too,
	 * so a diagonal drag arms the gesture as well.
	 */
	private double DragStartY;
	/**
	 * True once the active drag passed {@link #DRAG_THRESHOLD}
	 */
	private boolean DragActive;
	/**
	 * The face currently being dragged, or null when no gesture is running
	 */
	private ATabButton DraggedFace;
	/**
	 * Latest pointer X (scene coordinates) while a drag is in progress,
	 * written by the face's own drag handler and — once a reorder has
	 * re-parented the face — by the scene-level fallback, so the ticker always
	 * follows the most recent pointer position.
	 */
	private double DragPointerSceneX;
	/**
	 * Pointer distance (px) from the dragged face's left edge at grab time
	 */
	private double DragGrabOffsetX;
	/**
	 * Scene that carries the gesture-level fallback handlers while a drag is
	 * active; null when no gesture is running. Needed because the pressed
	 * face stops receiving mouse events the moment {@link #MoveTab} removes
	 * and re-adds it, so the scene is the only place the rest of the gesture
	 * can be observed from.
	 */
	private Scene GestureScene;
	/**
	 * Handler subscribed to {@link #GestureScene} for the duration of a
	 * gesture; null when not installed. Held in a field because the same
	 * instance has to be handed back to {@code removeEventFilter}.
	 */
	private EventHandler<MouseEvent> GestureHandler;
	/**
	 * Per-frame drag driver ({@link AAnimationTimer}): keeps the lifted face
	 * glued to the pointer, auto-scrolls the strip near the viewport edges and
	 * re-runs the insertion hit test, each frame passing the measured delta
	 * time in seconds so time-based motion stays refresh-rate independent.
	 * Started by {@link #BeginDrag}; stopped by {@link #EndDrag},
	 * {@link #CancelDrag} and {@link #TickDrag(double)} once the gesture is
	 * over.
	 */
	private final AAnimationTimer DragTicker;
	/**
	 * Strong reference to the listener {@link #DragTicker} fires every frame,
	 * subscribed once in the constructor and kept for the widget's lifetime.
	 * The timer holds its listeners weakly, so the handler must stay reachable
	 * from this widget or it could be collected between frames and the drag
	 * follow would stall.
	 */
	private final ITimeStepEvent DragStepListener;
	/**
	 * The currently selected tab index, or -1 if nothing is selected
	 */
	private int SelectedIndex = -1;
	/**
	 * Selection listeners (WeakReference, pruned on broadcast)
	 */
	private final List<WeakReference<ISubTabSelectionEvent>> SelectionListeners;
	/**
	 * New-tab-request listeners (WeakReference, pruned on broadcast)
	 */
	private final List<WeakReference<INewTabRequestEvent>> NewTabRequestListeners;
	/**
	 * Tab-closed listeners (WeakReference, pruned on broadcast)
	 */
	private final List<WeakReference<ITabCloseEvent>> TabCloseListeners;
	/**
	 * Tab-order-changed listeners (WeakReference, pruned on broadcast)
	 */
	private final List<WeakReference<ITabOrderChangeEvent>> OrderChangeListeners;

	/**
	 * Create a modifiable or fixed tab widget with the {@code NORMAL} skin
	 * variant.
	 *
	 * @param _Modifiable true to let the user add ({@code "+"}), close
	 *                    ({@code ×}) and drag-reorder tabs
	 */
	public ATabWidget(boolean _Modifiable)
	{
		this(_Modifiable, ETabButtonVariant.NORMAL);
	}

	/**
	 * Create a modifiable or fixed tab widget with an explicit tab-button
	 * skin size variant.
	 *
	 * @param _Modifiable true to let the user add ({@code "+"}), close
	 *                    ({@code ×}) and drag-reorder tabs
	 * @param _Variant    the {@link TabButtonSkin} size variant to bake into
	 *                    this strip's stylesheet ({@code NORMAL} for main
	 *                    tabs, {@code SMALL} for compact secondary strips)
	 */
	public ATabWidget(boolean _Modifiable, ETabButtonVariant _Variant)
	{
		super();

		Modifiable = _Modifiable;
		TabVariant = _Variant;
		TabButtons = new ArrayList<>();
		TabContents = new ArrayList<>();
		SelectionListeners = new ArrayList<>();
		NewTabRequestListeners = new ArrayList<>();
		TabCloseListeners = new ArrayList<>();
		OrderChangeListeners = new ArrayList<>();

		// Inner header: holds only the tab faces (no affix). Sits inside the
		// scroll pane, which clips and scrolls it when faces overflow.
		Header = new HBox();

		// Scroll pane: horizontal scroll on demand, no vertical bar; chrome
		// stripped so the outer header bar's background shows through cleanly.
		// Setting the hbar policy to NEVER prevents the skin from mounting a bar
		// node at all, avoiding a post-attach hide hack while preserving
		// programmatic and InstallWheelScroll() scrolling.
		HeaderScrollPane = new ScrollPane(Header);
		HeaderScrollPane.setFitToWidth(false);
		HeaderScrollPane.setFitToHeight(true);
		HeaderScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		HeaderScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		HeaderScrollPane.setBackground(Background.EMPTY);
		HeaderScrollPane.setBorder(Border.EMPTY);
		// Keep the scroll viewport itself at the compact face height: fit-to-height
		// otherwise lets the ScrollPane expand and leaves excess vertical space in
		// the SMALL header even though the tab faces are height-capped.
		if (TabVariant == ETabButtonVariant.SMALL)
		{
			HeaderScrollPane.setMinHeight(TAB_FACE_SMALL_MAX_HEIGHT);
			HeaderScrollPane.setPrefHeight(TAB_FACE_SMALL_MAX_HEIGHT);
			HeaderScrollPane.setMaxHeight(TAB_FACE_SMALL_MAX_HEIGHT);
		}
		InstallWheelScroll();

		// Outer header bar: the full-width background strip. The scroll pane
		// grows to absorb extra space; the "+" affix (if any) stays pinned at
		// its natural width on the right.
		HeaderBar = new HBox();
		HeaderBar.getStyleClass().add("a-tab-header");
		HBox.setHgrow(HeaderScrollPane, Priority.ALWAYS);
		HeaderBar.getChildren().add(HeaderScrollPane);

		if (_Modifiable)
		{
			btn_AddTab = new ATabButton("+");
			btn_AddTab.SetOnAction(this::FireNewTabRequest);
			HeaderBar.getChildren().add(btn_AddTab);
		}
		else
		{
			btn_AddTab = null;
		}

		Content = new StackPane();
		Content.getStyleClass().add("a-tab-content");

		getChildren().addAll(HeaderBar, Content);
		VBox.setVgrow(HeaderBar, Priority.NEVER);
		// Content must grow to fill the VBox; the header bar stays at its natural height
		VBox.setVgrow(Content, Priority.ALWAYS);

		ApplySkin();

		// Start from the framework default for the maximum tab face width; the
		// host sets the framework-wide default once at startup (GitAlpha applies
		// its TabMaxSize setting there), and per-widget overrides remain
		// available through SetTabMaxWidth().
		SetTabMaxWidth(DefaultTabMaxWidth);

		// The drag ticker runs only while a reorder gesture is active. A
		// per-frame driver is required (instead of reacting to mouse events
		// only) because the pointer can stand still near a viewport edge while
		// the strip must keep scrolling and the tab must keep crossing the
		// faces that scroll past it. AAnimationTimer hands TickDrag the elapsed
		// seconds of the frame, so the auto-scroll speed does not depend on the
		// display refresh rate. A timer only re-baselines on its first frame
		// after start(), which is why BeginDrag positions the face itself
		// instead of waiting for the first tick.
		DragTicker = new AAnimationTimer();
		DragStepListener = this::TickDrag;
		DragTicker.AddITimeStepEvent(DragStepListener);

		// Weak-reference registration: cleanup is automatic when the widget leaves the scene graph
		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	/**
	 * Create a non-modifiable tab widget (no add/close/reorder chrome).
	 */
	public ATabWidget()
	{
		this(false);
	}

	/**
	 * Map wheel input onto the horizontal scroll position: the tab strip is
	 * a single row, so a vertical wheel would otherwise drive only the
	 * disabled vertical axis and the strip would be unscrollable with the
	 * bar hidden. Wheel forward scrolls toward the strip start, wheel back
	 * toward its end (same sign convention as a vertical scrollbar);
	 * tilt-wheel/trackpad deltaX is honoured when deltaY is zero. Consumed
	 * so the pane's own handler never double-scrolls.
	 */
	private void InstallWheelScroll()
	{
		HeaderScrollPane.addEventFilter(ScrollEvent.SCROLL, __Event ->
		{
			double __Delta = __Event.getDeltaY() != 0 ? __Event.getDeltaY() : __Event.getDeltaX();
			if (__Delta == 0)
				return;
			double __Min = HeaderScrollPane.getHmin();
			double __Max = HeaderScrollPane.getHmax();
			double __Value = Math.max(__Min, Math.min(__Max, HeaderScrollPane.getHvalue() - __Delta));
			HeaderScrollPane.setHvalue(__Value);
			__Event.consume();
		});
	}

	/**
	 * Add a tab to the widget. The content node is kept alive for the
	 * widget's lifetime (the StackPane shows whichever child was last swapped
	 * in). The first tab added is selected automatically; later tabs require
	 * {@link #SelectTab}. In modifiable mode the new face is appended to the
	 * scrollable strip (the {@code "+"} affix stays pinned to the right).
	 *
	 * @param _Title   the tab label
	 * @param _Content the view to show when this tab is selected
	 * @return the zero-based index of the newly added tab
	 */
	public int AddTab(String _Title, Node _Content)
	{
		int __Index = TabButtons.size();
		ATabButton __Button = CreateTabButton(_Title);
		TabButtons.add(__Button);
		TabContents.add(_Content);
		Header.getChildren().add(__Button);

		// Select the first tab automatically so the content is never empty
		// after the first AddTab; later tabs require an explicit SelectTab.
		if (__Index == 0)
			SelectTab(0);

		return __Index;
	}

	/**
	 * Build a tab face for {@link #AddTab}: selection wired to its live
	 * position; in modifiable mode also the close button and drag handlers.
	 * The face's width is configured from the current min/max/pref settings.
	 *
	 * @param _Title the tab label
	 * @return the wired face (not yet inserted anywhere)
	 */
	private ATabButton CreateTabButton(String _Title)
	{
		ATabButton __Button = new ATabButton(_Title, Modifiable);
		// The face can still fire after its tab was closed programmatically
		// (a release already queued), so resolve the index defensively rather
		// than passing -1 into SelectTab.
		__Button.SetOnAction(() ->
		{
			int __I = TabButtons.indexOf(__Button);
			if (__I >= 0)
				SelectTab(__I);
		});
		ApplyTabSizes(__Button);

		// SMALL variant: cap each tab face's height so the compact strip is
		// visibly shorter than the NORMAL main-tab strip.
		if (TabVariant == ETabButtonVariant.SMALL)
			__Button.setMaxHeight(TAB_FACE_SMALL_MAX_HEIGHT);

		if (!Modifiable)
			return __Button;

		__Button.SetOnClose(() ->
		{
			int __I = TabButtons.indexOf(__Button);
			if (__I >= 0)
				CloseTab(__I);
		});

		InstallDragHandlers(__Button);
		return __Button;
	}

	/**
	 * Attach press/drag/release handlers that implement cursor-following live
	 * reorder. A press only records the origin; once the pointer travels
	 * {@link #DRAG_THRESHOLD} pixels (in any direction, so diagonal drags arm
	 * too) the face lifts and the gesture is handed to {@link #TickDrag(double)},
	 * which re-glues the face to the cursor, auto-scrolls the strip and
	 * re-runs the hit test every frame. Drag events are consumed to prevent
	 * the enclosing {@link ScrollPane} from scrolling while a reorder gesture
	 * is in progress.
	 * <p>
	 * These handlers are only the start of the gesture. {@link #MoveTab}
	 * re-parents the face on the first reorder, after which JavaFX delivers it
	 * no further mouse events, so the follow and the release continue through
	 * the scene-level fallback installed by {@link #BeginDrag}. The press
	 * handler stays on the face on purpose: the close glyph consumes its own
	 * events, so a press there must never arm a drag.
	 *
	 * @param _Button the tab face to make draggable
	 */
	private void InstallDragHandlers(ATabButton _Button)
	{
		_Button.setOnMousePressed(__Event ->
		{
			// Defensive: a release lost off-window must not leave a face up.
			if (DragActive)
				CancelDrag();
			DragStartX = __Event.getSceneX();
			DragStartY = __Event.getSceneY();
			DragActive = false;
		});
		_Button.setOnMouseDragged(__Event ->
		{
			// Always consume: dragging a face is for reordering, not scrolling.
			__Event.consume();
			if (TabButtons.size() < 2)
				return;

			// The ticker owns the gesture from here on; it needs the raw
			// pointer X every frame, including while the pointer stands still.
			DragPointerSceneX = __Event.getSceneX();
			if (DragActive)
				return;

			double __Dx = DragPointerSceneX - DragStartX;
			double __Dy = __Event.getSceneY() - DragStartY;
			if (Math.hypot(__Dx, __Dy) <= DRAG_THRESHOLD)
				return;
			BeginDrag(_Button);
		});
		_Button.setOnMouseReleased(__Event -> EndDrag());
	}

	/**
	 * Lift the given face into a drag: stop any slide still running on it,
	 * remember the grab offset (measured from the press point, so the face
	 * does not jump when the threshold is crossed), raise it above its
	 * siblings, start the per-frame ticker and install the scene-level gesture
	 * fallback ({@link #InstallGestureHandlers()}) that carries the gesture
	 * once a reorder re-parents the face.
	 *
	 * @param _Face the face the press started on
	 */
	private void BeginDrag(ATabButton _Face)
	{
		DragActive = true;
		DraggedFace = _Face;
		DragGrabOffsetX = DragStartX - _Face.localToScene(0, 0).getX();

		// A slide from an earlier release — or from a permutation that has
		// just moved this face — writes the same translateX the follow is
		// about to own: freeze it (leaving the current offset in place, so the
		// face is not repositioned) and let ApplyDragPosition take over.
		_Face.StopSlide();

		_Face.SetDragging(true);
		// viewOrder raises the lifted face without reordering the children
		// (toFront() would corrupt the tab order this class indexes by).
		_Face.setViewOrder(-1);

		ApplyDragPosition();
		DragTicker.start();
		InstallGestureHandlers();
	}

	/**
	 * Subscribe the gesture-level fallback handlers on the scene for the
	 * duration of a drag.
	 * <p>
	 * This exists because of how JavaFX delivers a drag: {@code MOUSE_DRAGGED}
	 * and {@code MOUSE_RELEASED} are sent to the node that was pressed, and
	 * that binding is dropped for good when the node leaves the scene graph.
	 * {@link #MoveTab} re-parents the dragged face every time the order
	 * changes ({@code remove} + {@code add} on the header), after which the
	 * face itself receives no further mouse events — its
	 * {@code setOnMouseDragged} stops updating the pointer and its
	 * {@code setOnMouseReleased} never fires, leaving the ticker chasing a
	 * frozen pointer with {@code DragActive} stuck on. The scene keeps
	 * receiving the same events, so the gesture continues from there.
	 * <p>
	 * Installed per gesture rather than once, so an idle widget subscribes to
	 * nothing.
	 */
	private void InstallGestureHandlers()
	{
		if (GestureScene != null)
			return;
		GestureScene = getScene();
		if (GestureScene == null)
			return;
		GestureHandler = this::OnSceneGesture;
		GestureScene.addEventFilter(MouseEvent.MOUSE_DRAGGED, GestureHandler);
		GestureScene.addEventFilter(MouseEvent.MOUSE_RELEASED, GestureHandler);
		// Also watch plain moves: if the button came up somewhere we never saw
		// the release for (pointer left the window, gesture cancelled by the
		// OS), a later move over the window is the only signal that the drag is
		// over.
		GestureScene.addEventFilter(MouseEvent.MOUSE_MOVED, GestureHandler);
	}

	/**
	 * Unsubscribe the gesture-level fallback handlers installed by
	 * {@link #InstallGestureHandlers()}. No-op when no gesture is running, so
	 * the end paths call it unconditionally — {@link #EndDrag} and
	 * {@link #CancelDrag} both invoke it before touching any drag state, which
	 * is what makes them safe to reach from a stale or already-ended gesture.
	 */
	private void RemoveGestureHandlers()
	{
		if (GestureScene == null)
			return;
		GestureScene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, GestureHandler);
		GestureScene.removeEventFilter(MouseEvent.MOUSE_RELEASED, GestureHandler);
		GestureScene.removeEventFilter(MouseEvent.MOUSE_MOVED, GestureHandler);
		GestureScene = null;
		GestureHandler = null;
	}

	/**
	 * Scene-level continuation of the reorder gesture, installed as a filter on
	 * the scene so it runs before target handlers and therefore also sees the
	 * events a face consumes: it feeds the ticker the pointer X the pressed
	 * face can no longer report, ends the gesture on a release, and ends it on
	 * the first move whose primary button is already up (a release lost when
	 * the pointer left the window). Overlapping with the face handlers is
	 * harmless — both write the same pointer value — and it returns
	 * immediately once no drag is active.
	 *
	 * @param _Event the scene-level mouse event
	 */
	private void OnSceneGesture(MouseEvent _Event)
	{
		// A gesture can be ended by the event in flight (release) or dropped by
		// a close; either way nothing more to do for this one.
		if (!DragActive)
			return;

		if (_Event.getEventType() == MouseEvent.MOUSE_RELEASED)
		{
			DragPointerSceneX = _Event.getSceneX();
			EndDrag();
			return;
		}
		if (_Event.getEventType() == MouseEvent.MOUSE_DRAGGED)
		{
			DragPointerSceneX = _Event.getSceneX();
			return;
		}
		// MOUSE_MOVED: no drag is in progress any more, so the primary button
		// must be up — a release we never got to see.
		if (!_Event.isPrimaryButtonDown())
			EndDrag();
	}

	/**
	 * Per-frame drag driver, registered as the {@link #DragTicker} step
	 * listener in the constructor: auto-scrolls the strip when the pointer nears
	 * a viewport edge, re-glues the lifted face to the cursor (scrolling and
	 * permutations move its slot, so the translate must be recomputed every
	 * frame) and re-runs the insertion hit test, re-gluing once more whenever
	 * that test permutes the strip. The pointer X it follows is whatever
	 * {@link #DragPointerSceneX} last recorded — the face's own drag handler
	 * before a reorder, the scene-level fallback afterwards — so the follow
	 * keeps tracking the cursor even though the pressed face has stopped
	 * receiving events. Stops the ticker as soon as it finds no active gesture
	 * left, and aborts the drag when the dragged face is gone (tab closed
	 * programmatically mid-gesture).
	 *
	 * @param _DeltaTime elapsed seconds since the previous frame, as measured
	 *                   and clamped by the timer; only the auto-scroll speed
	 *                   uses it, the follow itself reacts to absolute positions
	 */
	private void TickDrag(double _DeltaTime)
	{
		if (!DragActive || DraggedFace == null)
		{
			DragTicker.stop();
			return;
		}
		if (!TabButtons.contains(DraggedFace))
		{
			CancelDrag();
			return;
		}

		AutoScrollStrip(_DeltaTime);
		ApplyDragPosition();

		int __From = TabButtons.indexOf(DraggedFace);
		int __To = HitTestInsertIndex(DragPointerSceneX, __From);
		if (__To != __From)
		{
			MoveTab(__From, __To);
			// The permutation moved the face's slot: re-glue in the same frame
			// so the swap never shows up as a jump.
			ApplyDragPosition();
		}
	}

	/**
	 * Translate the lifted face so its left edge sits where it was grabbed
	 * relative to the current pointer. The correction is applied relative to
	 * the face's live scene position, which already accounts for the strip's
	 * scroll offset and for the slot the face occupies after a permutation,
	 * so the face stays glued to the cursor through both.
	 */
	private void ApplyDragPosition()
	{
		if (DraggedFace == null)
			return;

		double __TargetLeft = DragPointerSceneX - DragGrabOffsetX;
		double __CurrentLeft = DraggedFace.localToScene(0, 0).getX();
		DraggedFace.setTranslateX(DraggedFace.getTranslateX() + (__TargetLeft - __CurrentLeft));
	}

	/**
	 * Scroll the tab strip while the pointer hovers near a viewport edge, so
	 * tabs that are scrolled out of view stay reachable during a drag. Speed
	 * scales with the depth into the {@link #AUTO_SCROLL_EDGE_PX} band, topping
	 * out at {@code AUTO_SCROLL_MAX_PX * AUTO_SCROLL_REFERENCE_FPS} pixels per
	 * second at the very edge, and is multiplied by the frame's delta time — so
	 * the visual speed matches on 60 Hz and high-refresh displays while a
	 * stalled frame stays bounded instead of running away (the timer clamps the
	 * delta). The pixel delta is converted to a fraction of the scrollable range
	 * (content width minus viewport width) before adjusting
	 * {@link ScrollPane#hvalueProperty()}. No-op while the strip fits.
	 *
	 * @param _DeltaTime elapsed seconds since the previous frame; scales the
	 *                   auto-scroll pixel speed, so a doubled frame scrolls
	 *                   twice as far
	 */
	private void AutoScrollStrip(double _DeltaTime)
	{
		Bounds __Viewport = HeaderScrollPane.localToScene(HeaderScrollPane.getLayoutBounds());
		double __Range = Header.getLayoutBounds().getWidth() - HeaderScrollPane.getLayoutBounds().getWidth();
		if (__Range <= 0)
			return;

		// Turn the per-reference-frame tuning into a per-second speed and scale
		// it by this frame's duration: at AUTO_SCROLL_REFERENCE_FPS the product
		// collapses back to AUTO_SCROLL_MAX_PX pixels, and a slower pulse
		// scrolls proportionally further instead of crawling.
		double __MaxPixels = AUTO_SCROLL_MAX_PX * AUTO_SCROLL_REFERENCE_FPS * _DeltaTime;
		double __Pixels = 0;
		double __LeftEdge = __Viewport.getMinX() + AUTO_SCROLL_EDGE_PX;
		double __RightEdge = __Viewport.getMaxX() - AUTO_SCROLL_EDGE_PX;
		if (DragPointerSceneX < __LeftEdge)
			__Pixels = -__MaxPixels * Math.min(1.0, (__LeftEdge - DragPointerSceneX) / AUTO_SCROLL_EDGE_PX);
		else if (DragPointerSceneX > __RightEdge)
			__Pixels = __MaxPixels * Math.min(1.0, (DragPointerSceneX - __RightEdge) / AUTO_SCROLL_EDGE_PX);
		if (__Pixels == 0)
			return;

		double __Value = Math.max(0.0, Math.min(1.0, HeaderScrollPane.getHvalue() + __Pixels / __Range));
		if (__Value == HeaderScrollPane.getHvalue())
			return;

		HeaderScrollPane.setHvalue(__Value);
		// Apply the scroll immediately instead of leaving it to this pulse's
		// layout pass: the re-glue that follows reads the header's scene
		// position, and a deferred scroll would make the lifted face lag one
		// frame's worth of scrolling behind the cursor.
		HeaderScrollPane.layout();
	}

	/**
	 * Finish the active drag: drop the scene-level gesture handlers, stop the
	 * ticker, drop the lifted styling and ease the face from wherever the
	 * cursor left it into its slot. Reached from the face's own release
	 * handler, or — once a reorder has cost the face its mouse events — from
	 * the scene-level release or from the stale-release detection in
	 * {@link #OnSceneGesture(MouseEvent)}. The handler teardown runs first and
	 * unconditionally, which makes the call idempotent and also cleans up a
	 * gesture that was already gone. A plain click (threshold never crossed)
	 * just clears the flags.
	 */
	private void EndDrag()
	{
		// Idempotent, and also the cleanup for the stale-gesture path below.
		RemoveGestureHandlers();

		if (!DragActive || DraggedFace == null)
		{
			DragActive = false;
			return;
		}

		DragTicker.stop();
		ATabButton __Face = DraggedFace;
		DraggedFace = null;
		DragActive = false;

		// The lifted *look* ends now, but the lifted z-order does not: the box
		// shadow comes off immediately while the face still has to glide the
		// last few pixels home, and dropping viewOrder with it would let the
		// neighbours — which are sliding at the same time — paint over the face
		// that was just dropped.
		ReleaseDragFace(__Face);
		// Ease home from wherever the cursor left it; the z-order only comes
		// back once the face has landed (the slide reports it, and also does so
		// when it snaps straight to zero because the leftover offset is
		// sub-pixel).
		__Face.SlideFrom(__Face.getTranslateX(), () -> __Face.setViewOrder(0));

		// A reorder is also an activation: the button's own arming may or may
		// not survive the follow (the face is glued to the cursor, so JavaFX
		// can disarm it on a lagging frame), so select explicitly instead of
		// depending on whether ButtonBase fired.
		int __Index = TabButtons.indexOf(__Face);
		if (__Index >= 0)
			SelectTab(__Index);
	}

	/**
	 * Abort the active drag without the slide: drop the scene-level gesture
	 * handlers, stop the ticker and reset the face — used when the dragged
	 * face disappears (the tab was closed programmatically mid-drag) or when a
	 * fresh press finds a stale gesture left over from a lost release.
	 */
	private void CancelDrag()
	{
		RemoveGestureHandlers();
		DragTicker.stop();
		if (DraggedFace != null)
		{
			DraggedFace.StopSlide();
			DraggedFace.setTranslateX(0);
			ReleaseDragFace(DraggedFace);
			// No slide to wait for: the face is simply back in its slot.
			DraggedFace.setViewOrder(0);
		}
		DraggedFace = null;
		DragActive = false;
	}

	/**
	 * Drop the lifted styling of a face: clears the {@code :dragging}
	 * pseudo-class. The raised z-order is deliberately not touched here — it
	 * outlives the styling and is handed back by the caller, either
	 * immediately ({@link #CancelDrag}) or once the face has glided into its
	 * slot ({@link #EndDrag}).
	 *
	 * @param _Face the face leaving the drag
	 */
	private void ReleaseDragFace(ATabButton _Face)
	{
		_Face.SetDragging(false);
	}

	/**
	 * Resolve the insertion slot for the dragged tab: the number of other
	 * faces whose slot midpoint sits left of the pointer, clamped to the valid
	 * range. Slot positions ({@code layoutX} inside the header, translate
	 * excluded) are used instead of live visual bounds, so an in-flight slot
	 * slide can never feed back into the hit test and make the order
	 * oscillate; adding the header's own scene origin keeps the comparison
	 * correct while the strip is scrolled. Skipping the dragged face itself
	 * keeps the count stable while it follows the pointer.
	 *
	 * @param _SceneX pointer X in scene coordinates
	 * @param _From   current index of the dragged tab
	 * @return the target index for the dragged tab
	 */
	private int HitTestInsertIndex(double _SceneX, int _From)
	{
		double __HeaderSceneX = Header.localToScene(0, 0).getX();
		int __Insert = 0;
		for (int __I = 0; __I < TabButtons.size(); __I++)
		{
			if (__I == _From)
				continue;
			ATabButton __B = TabButtons.get(__I);
			double __Mid = __HeaderSceneX + __B.getLayoutX() + __B.getWidth() / 2.0;
			if (_SceneX > __Mid)
				__Insert++;
		}
		return Math.min(__Insert, TabButtons.size() - 1);
	}

	/**
	 * Permute a tab from one index to another: the face moves inside the
	 * inner header, the content list follows, and {@code SelectedIndex} is
	 * remapped with the standard single-element-move shift. Every other face
	 * keeps the pixel position the eye last saw it at and slides into its new
	 * slot through {@link ATabButton#SlideFrom(double)}, so a live drag reads
	 * as tabs making room instead of snapping. The dragged face is skipped —
	 * its translate is owned by {@link #ApplyDragPosition()}. The
	 * {@code remove}/{@code add} pair also costs the dragged face its JavaFX
	 * mouse-event binding, which is what the scene-level gesture fallback
	 * installed by {@link #InstallGestureHandlers()} exists to cover.
	 *
	 * @param _From source index
	 * @param _To   target index
	 */
	private void MoveTab(int _From, int _To)
	{
		// Freeze every face's slide before snapshotting: a running slide writes
		// the same translateX the snapshot reads, and a mid-animation value
		// would be captured as the "where the eye saw it" origin.
		for (ATabButton __Face : TabButtons)
			__Face.StopSlide();

		Map<ATabButton, Double> __VisualX = new HashMap<>();
		for (ATabButton __Face : TabButtons)
			__VisualX.put(__Face, __Face.getLayoutX() + __Face.getTranslateX());

		ATabButton __Button = TabButtons.remove(_From);
		Node __MovedContent = TabContents.remove(_From);
		TabButtons.add(_To, __Button);
		TabContents.add(_To, __MovedContent);

		Header.getChildren().remove(__Button);
		Header.getChildren().add(_To, __Button);

		// Remap the selection to follow the moved element
		if (SelectedIndex == _From)
			SelectedIndex = _To;
		else if (_From < SelectedIndex && _To >= SelectedIndex)
			SelectedIndex--;
		else if (_From > SelectedIndex && _To <= SelectedIndex)
			SelectedIndex++;

		// Lay the header out now rather than on the next pulse, so the new
		// slot positions are known and each slide can start from the exact
		// offset between "where the face was seen" and "where it now lives".
		Header.layout();

		for (Map.Entry<ATabButton, Double> __Entry : __VisualX.entrySet())
		{
			ATabButton __Face = __Entry.getKey();
			if (__Face == DraggedFace)
				continue;
			double __Offset = __Entry.getValue() - __Face.getLayoutX();
			// SlideFrom snaps sub-threshold offsets to zero without starting a
			// timer, so no separate epsilon check is needed here.
			__Face.SlideFrom(__Offset);
		}

		// Last, so a listener reading the strip back always sees the completed
		// permutation: lists, header children and selection index all updated.
		FireTabOrderChanged(_From, _To);
	}

	/**
	 * Close the tab at the given index (user action): remove face, content
	 * and header entry; if it was selected, select the nearest surviving
	 * neighbour (same slot clamped, previous when it was last). Fires
	 * {@link ITabCloseEvent} afterwards so consumers can release resources.
	 * Closing the face that is currently being dragged also aborts that
	 * gesture, so a programmatic close can never leave the per-frame driver
	 * chasing a detached node.
	 *
	 * @param _Index the tab to close
	 * @throws IndexOutOfBoundsException if _Index is out of range
	 */
	public void CloseTab(int _Index)
	{
		if (_Index < 0 || _Index >= TabButtons.size())
			throw new IndexOutOfBoundsException("Tab index out of range: " + _Index);

		ATabButton __Button = TabButtons.remove(_Index);
		Node __RemovedContent = TabContents.remove(_Index);
		Header.getChildren().remove(__Button);
		// A face removed mid-slide would keep ticking on a detached node.
		__Button.StopSlide();
		// Programmatic close of the face being dragged: drop the gesture so the
		// ticker does not chase a detached node.
		if (__Button == DraggedFace)
			CancelDrag();
		boolean __WasSelected = SelectedIndex == _Index;
		if (_Index < SelectedIndex)
			SelectedIndex--;

		if (__WasSelected)
		{
			SelectedIndex = -1;
			if (!TabButtons.isEmpty())
				SelectTab(Math.min(_Index, TabButtons.size() - 1));
			else
				Content.getChildren().clear();
		}

		FireTabClosed(_Index, __RemovedContent);
	}

	/**
	 * Switch the selected tab by index. Updates the face selected states,
	 * swaps the content stack, and fires selection listeners. No-op if the
	 * index is already selected.
	 *
	 * @param _Index the tab to select (must be in range)
	 * @throws IndexOutOfBoundsException if _Index is out of range
	 */
	public void SelectTab(int _Index)
	{
		if (_Index == SelectedIndex)
			return;
		if (_Index < 0 || _Index >= TabButtons.size())
			throw new IndexOutOfBoundsException("Tab index out of range: " + _Index);

		for (int __I = 0; __I < TabButtons.size(); __I++)
			TabButtons.get(__I).SetSelected(__I == _Index);

		SelectedIndex = _Index;
		Node __Content = TabContents.get(_Index);
		Content.getChildren().setAll(__Content);

		FireSelection(_Index, __Content);
	}

	/**
	 * @return the zero-based index of the currently selected tab, or -1 if
	 * nothing is selected
	 */
	public int GetSelectedIndex()
	{
		return SelectedIndex;
	}

	/**
	 * Resolve the live index of the tab hosting the given content node.
	 * Indexes shift on reorder/close, so consumers holding a content
	 * reference should resolve through this at invocation time instead of
	 * caching an index.
	 *
	 * @param _Content the content node previously passed to {@link #AddTab}
	 * @return the current index of that tab, or -1 if it is not present
	 */
	public int IndexOf(Node _Content)
	{
		return TabContents.indexOf(_Content);
	}

	/**
	 * Change the label of the tab at the given index (e.g. when a hosted
	 * view's title changes). Does not affect selection or ordering.
	 *
	 * @param _Index the tab to retitle
	 * @param _Title the new label
	 * @throws IndexOutOfBoundsException if _Index is out of range
	 */
	public void SetTabTitle(int _Index, String _Title)
	{
		TabButtons.get(_Index).setText(_Title);
	}

	/**
	 * @return the number of tabs in the widget (the {@code "+"} affix excluded)
	 */
	public int GetTabCount()
	{
		return TabButtons.size();
	}

	/**
	 * @return true when the user may add, close and reorder tabs
	 */
	public boolean IsModifiable()
	{
		return Modifiable;
	}

	/**
	 * Snapshot of the tab contents, in current display order.
	 * <p>
	 * The list is a copy, not the live backing list: reordering and closing
	 * shift the real list underneath (a drag can permute it on any frame), so a
	 * caller iterating a snapshot cannot trip over a permutation. Indexes taken
	 * from the snapshot are only valid until the next reorder or close —
	 * resolve through {@link #IndexOf(Node)} when holding on to a node.
	 *
	 * @return an unmodifiable copy of the content nodes, index-aligned with
	 * {@link #GetTabHeaders()}
	 */
	public List<Node> GetTabContents()
	{
		return Collections.unmodifiableList(new ArrayList<>(TabContents));
	}

	/**
	 * Snapshot of the tab faces — the "headers" of the strip — in current
	 * display order, index-aligned with {@link #GetTabContents()}. The
	 * {@code "+"} affix is not a tab and is not included.
	 * <p>
	 * Like {@link #GetTabContents()}, this is a copy taken at call time: safe
	 * to iterate while a drag permutes the strip, but every position taken
	 * from it goes stale on the next reorder or close, so re-read it instead of
	 * caching it. The faces themselves are the live nodes — mutate them (title,
	 * size) rather than replacing them, since the widget indexes its own lists
	 * by identity.
	 *
	 * @return an unmodifiable copy of the tab faces
	 */
	public List<ATabButton> GetTabHeaders()
	{
		return Collections.unmodifiableList(new ArrayList<>(TabButtons));
	}

	// ── Tab face sizing ──────────────────────────────────────────────────

	/**
	 * Set the minimum width applied to every tab face (including the
	 * {@code "+"} affix). Pass {@link Control#USE_COMPUTED_SIZE} to revert
	 * to the default computed minimum.
	 *
	 * @param _Width the minimum width in pixels
	 */
	public void SetTabMinWidth(double _Width)
	{
		TabMinWidth = _Width;
		ApplyTabSizes();
	}

	/**
	 * Set the maximum width applied to every tab face (including the
	 * {@code "+"} affix). Pass {@link Control#USE_COMPUTED_SIZE} to revert
	 * to the computed maximum (unlimited). Reverting is not the same as the
	 * construction baseline: the constructor has already applied the
	 * framework-wide default — {@link #TAB_FACE_MAX_WIDTH_DEFAULT} pixels
	 * unless the host called {@link #SetDefaultTabMaxWidth} — so a computed
	 * maximum lets faces grow past that width.
	 *
	 * @param _Width the maximum width in pixels
	 */
	public void SetTabMaxWidth(double _Width)
	{
		TabMaxWidth = _Width;
		ApplyTabSizes();
	}

	/**
	 * Set the framework-wide default maximum tab face width applied by every
	 * {@link ATabWidget} constructed afterwards (existing widgets keep their
	 * current value). Hosts call this once at startup to feed a persisted
	 * preference in; the framework never reads application settings itself,
	 * and per-widget overrides stay available through {@link #SetTabMaxWidth}.
	 * <p>
	 * The value is process-wide and only sampled by constructors, so it is not
	 * a live setting: call it while starting up, before any strip exists.
	 *
	 * @param _Width the default maximum width in pixels
	 */
	public static void SetDefaultTabMaxWidth(double _Width)
	{
		DefaultTabMaxWidth = _Width;
	}

	/**
	 * Set the preferred width applied to every tab face (including the
	 * {@code "+"} affix). Pass {@link Control#USE_COMPUTED_SIZE} to revert
	 * to the default computed preferred width (natural size).
	 *
	 * @param _Width the preferred width in pixels
	 */
	public void SetTabPrefWidth(double _Width)
	{
		TabPrefWidth = _Width;
		ApplyTabSizes();
	}

	/**
	 * Apply the current min/max/pref widths to a single tab face.
	 *
	 * @param _Button the face to configure
	 */
	private void ApplyTabSizes(ATabButton _Button)
	{
		_Button.setMinWidth(TabMinWidth);
		_Button.setMaxWidth(TabMaxWidth);
		_Button.setPrefWidth(TabPrefWidth);
	}

	/**
	 * Apply the current min/max/pref widths to all existing faces (tab faces
	 * and the {@code "+"} affix).
	 */
	private void ApplyTabSizes()
	{
		for (ATabButton __Button : TabButtons)
			ApplyTabSizes(__Button);
		if (btn_AddTab != null)
			ApplyTabSizes(btn_AddTab);
	}

	// ── Event registration ───────────────────────────────────────────────

	/**
	 * Register a selection listener. Held weakly, so a listener that is
	 * garbage-collected without unsubscribing is pruned on the next fire.
	 *
	 * @param _Event the listener to notify on tab selection changes
	 * @apiNote Must be called on the FX thread.
	 */
	public void AddSelectionEvent(ISubTabSelectionEvent _Event)
	{
		SelectionListeners.add(new WeakReference<>(_Event));
	}

	/**
	 * Register a listener for {@code "+"} clicks. Fired only in modifiable
	 * mode; respond by calling {@link #AddTab}.
	 *
	 * @param _Event the listener to notify on new-tab requests
	 * @apiNote Must be called on the FX thread.
	 */
	public void AddNewTabRequestEvent(INewTabRequestEvent _Event)
	{
		NewTabRequestListeners.add(new WeakReference<>(_Event));
	}

	/**
	 * Register a listener fired after the user closes a tab.
	 *
	 * @param _Event the listener to notify on tab closes
	 * @apiNote Must be called on the FX thread.
	 */
	public void AddTabCloseEvent(ITabCloseEvent _Event)
	{
		TabCloseListeners.add(new WeakReference<>(_Event));
	}

	/**
	 * Register a listener fired after the tab order changes, i.e. after a
	 * drag-reorder permutes the strip. The new order is already in place when
	 * it fires, so read it back through {@link #GetTabContents()} /
	 * {@link #GetTabHeaders()} rather than from the event.
	 * <p>
	 * Only reorders fire it: {@link #AddTab} reports through its return value
	 * and a close through {@link ITabCloseEvent}. During one drag the listener
	 * runs once per slot crossing, so keep the handler cheap and idempotent
	 * instead of treating each call as a commit point.
	 * <p>
	 * Held weakly like the other listeners, so keep a strong reference to the
	 * listener (typically a field on the owning view) — one reachable only from
	 * this registration is collected and silently stops firing.
	 *
	 * @param _Event the listener to notify on tab order changes
	 * @apiNote Must be called on the FX thread; the listener is invoked on it
	 *          too, possibly several times within a single drag.
	 */
	public void AddTabOrderChangeEvent(ITabOrderChangeEvent _Event)
	{
		OrderChangeListeners.add(new WeakReference<>(_Event));
	}

	// ── Event broadcast ──────────────────────────────────────────────────

	/**
	 * Notify selection listeners, pruning dead references inline.
	 */
	private void FireSelection(int _Index, Node _Content)
	{
		int __I = 0;
		while (__I < SelectionListeners.size())
		{
			ISubTabSelectionEvent __Event = SelectionListeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event(_Index, _Content);
				__I++;
			}
			else
				SelectionListeners.remove(__I);
		}
	}

	/**
	 * Notify new-tab-request listeners, pruning dead references inline.
	 */
	private void FireNewTabRequest()
	{
		int __I = 0;
		while (__I < NewTabRequestListeners.size())
		{
			INewTabRequestEvent __Event = NewTabRequestListeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event();
				__I++;
			}
			else
				NewTabRequestListeners.remove(__I);
		}
	}

	/**
	 * Notify tab-closed listeners, pruning dead references inline.
	 */
	private void FireTabClosed(int _Index, Node _Content)
	{
		int __I = 0;
		while (__I < TabCloseListeners.size())
		{
			ITabCloseEvent __Event = TabCloseListeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event(_Index, _Content);
				__I++;
			}
			else
				TabCloseListeners.remove(__I);
		}
	}

	/**
	 * Notify tab-order-changed listeners, pruning dead references inline.
	 */
	private void FireTabOrderChanged(int _From, int _To)
	{
		int __I = 0;
		while (__I < OrderChangeListeners.size())
		{
			ITabOrderChangeEvent __Event = OrderChangeListeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event(_From, _To);
				__I++;
			}
			else
				OrderChangeListeners.remove(__I);
		}
	}

	// ── Theme ────────────────────────────────────────────────────────────

	/**
	 * Theme-change push: re-bake the skin with the new palette's colors.
	 * The single stylesheet on this VBox cascades to all descendants, so
	 * header strip, tab faces, close buttons and hairline re-bake together.
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplySkin();
	}

	/**
	 * Replace the inline skin stylesheet with one baked from the active
	 * palette for this widget's {@link TabVariant}. The data-URI URL changes
	 * whenever the colors do, so JavaFX re-parses the new skin. The selector
	 * chains target the descendant classes ({@code .a-tab-header},
	 * {@code .a-tab-button}, {@code .a-tab-close}, {@code .a-tab-content}) so
	 * the single sheet covers all four node kinds.
	 */
	private void ApplySkin()
	{
		getStylesheets().clear();
		getStylesheets().addAll(ThemeManager.Instance.GetTabButtonStylesheets(TabVariant));
	}
}
