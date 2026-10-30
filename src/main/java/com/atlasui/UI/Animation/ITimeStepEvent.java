package com.atlasui.UI.Animation;

/**
 * Callback receiving the per-frame delta time measured by an
 * {@link AAnimationTimer}.
 * <p>
 * The delta time is already converted for the caller: the timer differences two
 * consecutive {@code long} nanosecond pulse timestamps and divides by
 * {@code 1_000_000_000}, so the value arrives as seconds in a {@code double}
 * rather than in the integer nanoseconds {@link javafx.animation.AnimationTimer}
 * reports. On a 60 Hz display, where one second spans 60 frames, every call
 * therefore receives roughly {@code 1.0 / 60.0} (about {@code 0.01667} s);
 * on a 120 Hz display roughly {@code 1.0 / 120.0}. Multiplying a speed in
 * units-per-second by it advances one frame at a constant visual speed
 * regardless of the refresh rate.
 * <p>
 * The value is clamped to the timer's maximum so a stalled pulse never delivers
 * one huge jump. Listeners are held as weak references by the timer and pruned
 * on broadcast, matching the event-list pattern used by
 * {@link com.atlasui.UI.Components.ATabWidget} and
 * {@link com.atlasui.Theme.ThemeManager}.
 */
public interface ITimeStepEvent
{
	/**
	 * Called once per frame while the owning {@link AAnimationTimer} runs, on
	 * the FX application thread, after the elapsed time since the previous
	 * frame was measured, converted to seconds and clamped. The very first
	 * frame after a start only establishes the baseline and does not fire.
	 *
	 * @param _DeltaTime elapsed seconds since the previous frame, in
	 *                   {@code [0, GetMaxTimeStep()]}; multiply a speed in
	 *                   units-per-second by this to advance one frame
	 *                   independently of the display refresh rate
	 */
	void Event(double _DeltaTime);
}
