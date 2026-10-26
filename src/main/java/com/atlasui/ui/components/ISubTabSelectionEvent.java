package com.atlasui.ui.components;

import javafx.scene.Node;

/**
 * Callback fired when the active tab of an {@link ATabWidget} changes, after
 * the content stack has already been swapped to the newly selected node.
 * <p>
 * The widget holds listeners as weak references and prunes dead ones on
 * broadcast, matching the event-list pattern used by
 * {@link com.atlasui.theme.ThemeManager}. A consumer must therefore keep a
 * strong reference to its listener (for example a field on the owning widget):
 * a listener reachable only through the registration is collected and silently
 * stops receiving events.
 */
public interface ISubTabSelectionEvent
{
	/**
	 * Called on the FX thread when the selected tab changes. Selecting the
	 * index that is already selected is a no-op and fires nothing.
	 *
	 * @param _Index   the zero-based index of the newly selected tab
	 * @param _Content the content node now shown by the widget
	 */
	void Event(int _Index, Node _Content);
}
