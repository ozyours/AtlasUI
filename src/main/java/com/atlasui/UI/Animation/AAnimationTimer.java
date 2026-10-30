package com.atlasui.UI.Animation;

import javafx.animation.AnimationTimer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Frame driver that converts the JavaFX pulse clock into a
 * {@code double} time step in seconds and broadcasts it to
 * {@link ITimeStepEvent} listeners.
 * <p>
 * {@link AnimationTimer#handle(long)} reports the frame timestamp as a
 * {@code long} nanosecond reading, which has no meaning outside the runtime
 * and no notion of per-frame length. Code that moves something per frame
 * therefore has to derive the step itself; this class does that once — it
 * subtracts consecutive timestamps, converts nanoseconds to seconds, clamps
 * the result and hands it to every listener through
 * {@link ITimeStepEvent#Event(double)}. A listener can then advance motion as
 * {@code speedPerSecond * _DeltaTime} and stay independent of the refresh rate
 * (60 Hz, 120 Hz, or a stuttering pulse all move at the same visual speed).
 * <p>
 * Each step is clamped to the current ceiling, {@link #GetMaxTimeStep()},
 * which starts at {@link #DEFAULT_MAX_TIME_STEP} seconds and is changed with
 * {@link #SetMaxTimeStep(double)}. Without the ceiling a stalled pulse —
 * minimised window, blocked FX thread, debugger pause — would report the whole
 * gap as one step and teleport the driven state; the clamp turns such a stall
 * into a single slow frame instead. Non-positive deltas (a pulse clock that
 * steps backwards) are reported as {@code 0}.
 * <p>
 * The first frame after {@link #start()} only records the baseline and fires
 * nothing, since there is no previous timestamp to subtract. Listeners are
 * held as weak references and pruned on broadcast, so a listener that is
 * garbage-collected without unregistering cannot keep the timer alive — the
 * same event-list pattern as {@link com.atlasui.UI.Components.ATabWidget} and
 * {@link com.atlasui.Theme.ThemeManager}.
 * <p>
 * Not thread-safe: start/stop and every listener call happen on the FX
 * application thread.
 */
public class AAnimationTimer extends AnimationTimer
{
	/**
	 * Default ceiling (seconds) on a single reported step: high enough that a
	 * dropped frame passes through untouched, low enough that a long stall
	 * cannot teleport the driven state
	 */
	private static final double DEFAULT_MAX_TIME_STEP = 0.25;

	/** Nanoseconds per second; conversion between the pulse clock and the reported step */
	private static final double NANOS_PER_SECOND = 1_000_000_000.0;

	/** Registered step listeners (WeakReference, pruned on broadcast) */
	private final List<WeakReference<ITimeStepEvent>> Listeners;

	/** Largest step (seconds) a single frame may report */
	private double MaxTimeStep = DEFAULT_MAX_TIME_STEP;

	/** Timestamp of the previously handled frame; read only while {@link #HasBaseline} is set */
	private long LastNow;

	/** True once a frame has recorded {@link #LastNow} after the last start */
	private boolean HasBaseline;

	/** Step (seconds) reported on the most recent frame; 0 until a step has been reported */
	private double LastTimeStep;

	/** Sum of every step reported since construction or the last {@link #ResetElapsed()} */
	private double ElapsedSeconds;

	/**
	 * Create a stopped timer; call {@link #start()} to begin firing
	 * {@link ITimeStepEvent}s.
	 */
	public AAnimationTimer()
	{
		super();
		Listeners = new ArrayList<>();
	}

	/**
	 * Pulse callback: difference the pulse timestamp against the previous
	 * frame, convert nanoseconds to seconds, clamp it, accumulate it and
	 * broadcast it. Called by the JavaFX runtime once per frame while the timer
	 * is running — never invoke it directly.
	 * <p>
	 * At 60 frames per second the reported delta time is roughly
	 * {@code 1.0 / 60.0} (about {@code 0.01667} s); at 120 frames per second
	 * roughly {@code 1.0 / 120.0}. The unit conversion is
	 * {@code (_Now - LastNow) / 1_000_000_000.0}.
	 *
	 * @param _Now the frame timestamp in nanoseconds, supplied by the pulse
	 */
	@Override
	public void handle(long _Now)
	{
		// First frame after a start: there is nothing to subtract yet.
		if (!HasBaseline)
		{
			HasBaseline = true;
			LastNow = _Now;
			return;
		}

		double __DeltaTime = (_Now - LastNow) / NANOS_PER_SECOND;
		LastNow = _Now;

		// Clamp to [0, MaxTimeStep]: a stalled pulse must not surface as a
		// jump, and a backwards clock must not surface as negative time.
		if (__DeltaTime < 0)
			__DeltaTime = 0;
		else if (__DeltaTime > MaxTimeStep)
			__DeltaTime = MaxTimeStep;

		LastTimeStep = __DeltaTime;
		ElapsedSeconds += __DeltaTime;

		FireDeltaTime(__DeltaTime);
	}

	/**
	 * Start firing. Drops any stale baseline, so the first frame after a
	 * restart only re-baselines instead of reporting the whole time the timer
	 * was stopped as one step.
	 *
	 * @apiNote Must be called on the FX thread.
	 */
	@Override
	public void start()
	{
		HasBaseline = false;
		super.start();
	}

	/**
	 * Stop firing. The next {@link #start()} re-baselines, so the stopped
	 * interval never reaches {@link ITimeStepEvent} listeners.
	 *
	 * @apiNote Must be called on the FX thread.
	 */
	@Override
	public void stop()
	{
		HasBaseline = false;
		super.stop();
	}

	/**
	 * Register a listener for the per-frame step. Held weakly; register at most
	 * a long-lived object, since a lambda that is not referenced elsewhere may
	 * be collected between frames. Registration is not deduplicated, so a
	 * listener added twice is notified twice per frame.
	 *
	 * @apiNote Must be called on the FX thread.
	 *
	 * @param _Event the listener to notify on every frame
	 */
	public void AddITimeStepEvent(ITimeStepEvent _Event)
	{
		Listeners.add(new WeakReference<>(_Event));
	}

	/**
	 * Set the ceiling applied to a single step. Larger values let a stalled
	 * frame deliver more of the missed time at once (motion keeps up, but in a
	 * visible jump); smaller values smooth stalls out at the cost of running
	 * slow after one. Only frames measured after the call are affected — an
	 * already reported step and the accumulated elapsed time are left as is.
	 *
	 * @param _Seconds the maximum step in seconds, must be positive
	 * @throws IllegalArgumentException if _Seconds is not positive
	 */
	public void SetMaxTimeStep(double _Seconds)
	{
		if (!(_Seconds > 0))
			throw new IllegalArgumentException("Max time step must be positive: " + _Seconds);
		MaxTimeStep = _Seconds;
	}

	/**
	 * @return the ceiling applied to a single step, in seconds
	 */
	public double GetMaxTimeStep()
	{
		return MaxTimeStep;
	}

	/**
	 * @return seconds reported on the most recent frame, or 0 until the timer
	 *         has reported its first step; a stop/start leaves it untouched, so
	 *         it keeps the value measured before the restart
	 */
	public double GetLastTimeStep()
	{
		return LastTimeStep;
	}

	/**
	 * @return sum of every reported step since construction or the last
	 *         {@link #ResetElapsed()}, in seconds
	 */
	public double GetElapsedSeconds()
	{
		return ElapsedSeconds;
	}

	/**
	 * Reset the accumulated time to zero without touching the frame baseline,
	 * so the next frame reports a normal step.
	 */
	public void ResetElapsed()
	{
		ElapsedSeconds = 0;
	}

	/**
	 * Notify every live listener, pruning references whose listener was
	 * collected.
	 *
	 * @param _DeltaTime the clamped delta time in seconds
	 */
	private void FireDeltaTime(double _DeltaTime)
	{
		int __I = 0;
		while (__I < Listeners.size())
		{
			ITimeStepEvent __Event = Listeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event(_DeltaTime);
				__I++;
			}
			else
				Listeners.remove(__I);
		}
	}
}
