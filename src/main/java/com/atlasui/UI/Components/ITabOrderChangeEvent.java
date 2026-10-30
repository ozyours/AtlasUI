package com.atlasui.UI.Components;

/**
 * Callback fired when the tab order of a modifiable {@link ATabWidget}
 * changes, i.e. when the user drag-reorders a tab.
 * <p>
 * Fires after the permutation is complete: the faces, their contents and the
 * selected index have already been remapped, so a listener that reads
 * {@link ATabWidget#GetTabContents()} or {@link ATabWidget#GetTabHeaders()}
 * sees the new order. Adding and closing tabs do not fire this — a close
 * reports through {@link ITabCloseEvent} and an add returns the new index — so
 * a listener can treat it as "the same tabs, in a different sequence".
 * <p>
 * A drag reorders continuously, not just on the drop: this fires once for each
 * slot crossing while the pointer moves, and the pointer can wander back over a
 * boundary it already crossed. Listeners therefore run many times per gesture
 * and must be cheap and tolerant of repeats; defer expensive work (persisting
 * the order, rebuilding views) until the gesture settles rather than doing it
 * on every call.
 * <p>
 * The widget holds listeners as weak references and prunes dead ones on
 * broadcast, matching the event-list pattern used by
 * {@link com.atlasui.Theme.ThemeManager}. A consumer must therefore keep a
 * strong reference to its listener (for example a field on the owning widget):
 * a listener reachable only through the registration is collected and silently
 * stops receiving events.
 */
public interface ITabOrderChangeEvent
{
	/**
	 * Called on the FX thread after a tab was moved to a different slot. The
	 * drag gesture may still be running, so a listener must not reorder or
	 * close tabs from inside this call.
	 * <p>
	 * The indices describe this one permutation, not the gesture as a whole:
	 * {@code _From} is where the tab sat immediately before it moved, which is
	 * not necessarily where the drag started.
	 *
	 * @param _From the zero-based position the tab occupied before the move
	 * @param _To   the zero-based position it now occupies
	 */
	void Event(int _From, int _To);
}
