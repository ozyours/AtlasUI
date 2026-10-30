package com.atlasui.UI.Components;

import com.atlasui.UI.Animation.AAnimationTimer;
import com.atlasui.UI.Animation.ITimeStepEvent;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;

/**
 * Themed tab button: the tab face of {@link ATabWidget} and, in its plain
 * form, the {@code ×} close glyph hosted inside such a face.
 * <p>
 * Extends {@link Button} (not {@code AButton}) and carries the
 * {@code a-tab-button} style class. It owns no stylesheet of its own — it is
 * always hosted under an {@link ATabWidget}, whose single cascading tab skin
 * ({@code TabButtonSkin}) styles it, including the variant metrics
 * ({@code NORMAL} vs {@code SMALL}) of that strip. Keeping the styling on the
 * widget matters for the compound face: a node-level stylesheet would outrank
 * the ancestor's cascade and force main-tab metrics onto the {@code ×} close
 * faces of small sub-tab strips. The face's internal layout — title aligned
 * left, close glyph 2 px after it, no Modena focus ring — belongs to that skin
 * as well, so a face keeps its look in either variant and in both roles.
 * <p>
 * A closable face renders its title as the button text and the {@code ×} as
 * the button graphic; the close glyph is itself a plain (non-closable, no
 * graphic) {@code ATabButton} carrying {@code a-tab-close}, so one class
 * serves both roles. The close glyph consumes its own press/click/drag events,
 * which keeps a press on it from arming this face — closing a tab therefore
 * never selects it and never starts a drag-reorder.
 * <p>
 * The selected state is a custom {@code :selected} pseudo-class toggled via
 * {@link #SetSelected(boolean)}; the lifted-by-a-drag state is the custom
 * {@code :dragging} pseudo-class toggled via {@link #SetDragging(boolean)};
 * the pressed state is the button's own armed state ({@code :pressed}), driven
 * by {@code ButtonBase}. The CSS itself is owned centrally by the theme layer
 * ({@code TabButtonSkin}), so every tab button shares one style source and
 * re-applies on palette switches through its hosting widget's
 * {@code IThemeChangeEvent} registration.
 * <p>
 * The face also owns the slide animation that moves it into its strip slot.
 * {@link #SlideFrom(double)} places the face at a visual offset and lets it
 * fall back to {@code 0}, driven by a timer spawned for that single slide:
 * the face remembers the start offset and the time elapsed since the slide
 * began, and each frame sets the translation to
 * {@code start · exp(-rate · elapsed)} — an exponential ease-out that
 * decelerates as it lands and is independent of the display refresh rate.
 * Because that curve is asymptotic, the slide snaps to {@code 0} and stops
 * once the remaining offset drops below {@link #SLIDE_MINIMUM_DELTA} pixels.
 * Every slide spawns a fresh timer and releases it on landing, so an idle
 * strip runs no timers. A caller can also tie one-shot state to the slide
 * through {@link #SlideFrom(double, Runnable)}: the notification fires when
 * the face settles, and equally when the slide is frozen or replaced, so the
 * state is never stranded. The strip uses that to keep a dropped tab above its
 * neighbours until it has finished gliding into its slot.
 */
public class ATabButton extends Button
{
	/**
	 * The pseudo-class marking the selected (active) tab face
	 */
	private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

	/**
	 * The pseudo-class marking the face currently lifted by a drag-reorder
	 */
	private static final PseudoClass DRAGGING = PseudoClass.getPseudoClass("dragging");

	/**
	 * Decay rate (per second) of the slide's exponential ease-out: after
	 * {@code t} seconds a fraction {@code exp(-rate · t)} of the offset is
	 * still left. At 35 the face covers ~95% of the travel in ~86 ms and
	 * settles under {@link #SLIDE_MINIMUM_DELTA} in ~150 ms from a 100 px
	 * offset — close to the ~130 ms a slot swap is meant to take, so a swap
	 * still reads as quick as the strip always did.
	 */
	private static final double SLIDE_DECAY_PER_SECOND = 35;

	/**
	 * Smallest remaining offset (pixels) the slide still animates: the
	 * exponential curve approaches zero without reaching it, so the face snaps
	 * to {@code 0} and stops the timer once it is this close. Also the offset
	 * below which {@link #SlideFrom(double)} does not start an animation at
	 * all.
	 */
	private static final double SLIDE_MINIMUM_DELTA = 0.5;

	/**
	 * Invoked on primary click, Space or Enter; null until
	 * {@link #SetOnAction(Runnable)}
	 */
	private Runnable OnActionHandler;

	/**
	 * Invoked when the close glyph is activated; null when the face is not
	 * closable
	 */
	private Runnable OnCloseHandler;

	/**
	 * Timer spawned for the current slide, or null when no slide is running.
	 * A slide creates its own timer and releases it on landing, so an idle
	 * strip holds no per-face pulse receivers
	 */
	private AAnimationTimer SlideTimer;

	/**
	 * Strong reference to {@link #SlideTimer}'s step listener. The timer holds
	 * listeners weakly, so the handler has to stay reachable from this button
	 * or the slide could stall between frames. Released with the timer.
	 */
	private ITimeStepEvent SlideStepListener;

	/** Offset (pixels) the current slide started from, scaled by the decay curve */
	private double SlideFromX;

	/** Seconds accumulated since the current slide started; drives the decay */
	private double SlideElapsed;

	/**
	 * One-shot notification belonging to the slide running now, or null when
	 * nothing is pending. Cleared right before it runs.
	 */
	private Runnable SlideSettledHandler;

	/**
	 * Create a plain tab button: no close glyph, no graphic and no drag
	 * wiring. Used as a tab face by {@link ATabWidget} and as the {@code ×}
	 * close glyph inside a closable face. Must be hosted under an
	 * {@link ATabWidget} (or another ancestor carrying the cascading tab
	 * stylesheet) to be visually themed.
	 *
	 * @param _Text the button label
	 */
	public ATabButton(String _Text)
	{
		this(_Text, false);
	}

	/**
	 * Create a tab face, optionally with the {@code ×} close glyph.
	 * <p>
	 * Activation mirrors the strip's expectations: primary click and Space are
	 * the button's own bindings, Enter is routed here, and the close glyph
	 * swallows the mouse events that would otherwise arm this face.
	 *
	 * @param _Text     the tab label
	 * @param _Closable true to render the {@code ×} close glyph as the graphic
	 */
	public ATabButton(String _Text, boolean _Closable)
	{
		super(_Text);
		getStyleClass().add("a-tab-button");
		// Button parses '_' as a mnemonic by default; a tab title is a file or
		// document name, so the character has to be shown literally.
		setMnemonicParsing(false);
		// Reachable via Tab traversal; Space fires through ButtonBase, Enter is
		// not part of its key map and is routed to fire() below.
		setFocusTraversable(true);
		setOnAction(__Event -> FireAction());
		setOnKeyPressed(__Event ->
		{
			if (__Event.getCode() == KeyCode.ENTER)
			{
				__Event.consume();
				fire();
			}
		});

		if (!_Closable)
			return;

		// The close glyph is the same component in its plain form: it inherits
		// the face's font metrics and is flattened by the skin's compound
		// .a-tab-button.a-tab-close rules.
		ATabButton __Close = new ATabButton("×");
		__Close.getStyleClass().add("a-tab-close");
		// The old close face was a non-focusable graphic; keep it out of Tab
		// traversal so it does not precede real controls.
		__Close.setFocusTraversable(false);
		// Consume press + click + drag so the close neither arms (and thus
		// selects) the host face nor starts a drag-reorder on it — dragged
		// events are retargeted to the press node, so consuming here keeps the
		// whole gesture off the face.
		__Close.setOnMousePressed(MouseEvent::consume);
		__Close.setOnMouseClicked(MouseEvent::consume);
		__Close.setOnMouseDragged(MouseEvent::consume);
		__Close.SetOnAction(this::FireClose);
		setGraphic(__Close);
		// 2 px between title and glyph, matching the gap the face's children
		// had before the face became a Button. TabButtonSkin declares the same
		// value through -fx-graphic-text-gap for the themed path.
		setGraphicTextGap(2);
	}

	/**
	 * Mark this button as the selected (active) tab. The {@code :selected}
	 * pseudo-class drives the brighter selected skin; unselecting restores the
	 * inactive tab look.
	 *
	 * @param _Selected true to select, false to deselect
	 */
	public void SetSelected(boolean _Selected)
	{
		pseudoClassStateChanged(SELECTED, _Selected);
	}

	/**
	 * Mark this face as lifted by a drag-reorder. The {@code :dragging}
	 * pseudo-class drives the raised skin (brighter shade + drop shadow +
	 * closed-hand cursor) and outranks {@code :selected} / {@code :pressed} in
	 * the baked stylesheet.
	 *
	 * @param _Dragging true while the face follows the pointer
	 */
	public void SetDragging(boolean _Dragging)
	{
		pseudoClassStateChanged(DRAGGING, _Dragging);
	}

	/**
	 * Register the activation action (select this tab, or request a new one
	 * for the {@code "+"} affix). Fires on primary click, Space and Enter.
	 *
	 * @param _OnAction invoked when the button is activated
	 */
	public void SetOnAction(Runnable _OnAction)
	{
		OnActionHandler = _OnAction;
	}

	/**
	 * Register the close action. Ignored on faces created without a close
	 * glyph (e.g. the {@code "+"} affix).
	 *
	 * @param _OnClose invoked when the close glyph is activated
	 */
	public void SetOnClose(Runnable _OnClose)
	{
		OnCloseHandler = _OnClose;
	}

	/**
	 * Start the slide that returns this face to its layout slot: place it at
	 * the given visual offset, then interpolate that offset down to {@code 0}
	 * with an exponential ease-out, one step per frame, and release the timer
	 * once the face has landed.
	 * <p>
	 * Every call spawns a new {@link AAnimationTimer}; one already running on
	 * this face is stopped and replaced, and the elapsed clock is reset, so the
	 * curve always restarts from the newest offset and the newest offset wins.
	 * Offsets below {@link #SLIDE_MINIMUM_DELTA} are applied as an immediate
	 * snap instead, so a strip at rest never pays for a timer.
	 *
	 * @apiNote Must be called on the FX thread.
	 *
	 * @param _From starting offset in header pixels; the sign gives the
	 *              direction the face has to travel
	 */
	public void SlideFrom(double _From)
	{
		SlideFrom(_From, null);
	}

	/**
	 * Start the slide and report back when it is over, whether it eased all
	 * the way home, was replaced by a newer slide, or was frozen by
	 * {@link #StopSlide()}. Lets the caller tie a one-shot piece of state to
	 * the slide — the strip uses it to keep a dropped tab above its siblings
	 * until it has settled in its slot — without owning a timer itself.
	 * <p>
	 * The notification is one-shot and belongs to this slide only: it is
	 * cleared before it runs, so a handler that starts another slide does not
	 * recurse.
	 *
	 * @apiNote Must be called on the FX thread.
	 *
	 * @param _From      starting offset in header pixels; the sign gives the
	 *                   direction the face has to travel
	 * @param _OnSettled run once when this slide ends; null for no
	 *                   notification
	 */
	public void SlideFrom(double _From, Runnable _OnSettled)
	{
		// Ends (and reports) a slide still running from an earlier call before
		// this one's handler is stored, so the two never cross.
		StopSlide();

		SlideSettledHandler = _OnSettled;

		if (Math.abs(_From) < SLIDE_MINIMUM_DELTA)
		{
			setTranslateX(0);
			StopSlide();
			return;
		}

		SlideFromX = _From;
		SlideElapsed = 0;
		setTranslateX(_From);

		SlideTimer = new AAnimationTimer();
		SlideStepListener = this::TickSlide;
		SlideTimer.AddITimeStepEvent(SlideStepListener);
		SlideTimer.start();
	}

	/**
	 * Stop the slide and release its timer, leaving the translate untouched so
	 * a caller can claim it (the drag follow does). The per-slide timer and its
	 * step listener are both dropped, so nothing is retained for the rest of
	 * the face's lifetime. A slide that is stopped early still reports to its
	 * {@link #SlideFrom(double, Runnable) settle handler}: from the caller's
	 * point of view the slide is over either way, and that is what releases
	 * the state tied to it.
	 *
	 * @apiNote Must be called on the FX thread.
	 */
	public void StopSlide()
	{
		// Cleared before running: the handler may start another slide, which
		// would otherwise find this one still pending.
		Runnable __Settled = SlideSettledHandler;
		SlideSettledHandler = null;

		if (SlideTimer != null)
		{
			SlideTimer.stop();
			SlideTimer = null;
			SlideStepListener = null;
		}

		if (__Settled != null)
			__Settled.run();
	}

	/**
	 * Per-frame slide step, registered as the spawned {@link #SlideTimer}'s
	 * step listener: accumulate the frame time and set the translation to the
	 * start offset scaled by {@code exp(-rate · elapsed)}, an exponential
	 * ease-out — fast at first, asymptotically slow as it lands. Measuring
	 * from the start offset rather than decaying the current translate keeps
	 * the curve exact when frames are dropped. Snaps to {@code 0}, stops the
	 * timer and releases it once the remainder drops below
	 * {@link #SLIDE_MINIMUM_DELTA}.
	 *
	 * @param _DeltaTime elapsed seconds since the previous frame, as measured
	 *                   and clamped by the timer; a non-positive step leaves
	 *                   the face where it is
	 */
	private void TickSlide(double _DeltaTime)
	{
		SlideElapsed += _DeltaTime;

		double __Remaining = SlideFromX * Math.exp(-SLIDE_DECAY_PER_SECOND * SlideElapsed);
		if (Math.abs(__Remaining) < SLIDE_MINIMUM_DELTA)
		{
			setTranslateX(0);
			StopSlide();
			return;
		}
		setTranslateX(__Remaining);
	}

	/** Run the activation action if one is registered. */
	private void FireAction()
	{
		if (OnActionHandler != null)
			OnActionHandler.run();
	}

	/** Run the close action if one is registered. */
	private void FireClose()
	{
		if (OnCloseHandler != null)
			OnCloseHandler.run();
	}
}
