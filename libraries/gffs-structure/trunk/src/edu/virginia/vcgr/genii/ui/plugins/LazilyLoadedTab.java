package edu.virginia.vcgr.genii.ui.plugins;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class LazilyLoadedTab extends JPanel
{
	static final long serialVersionUID = 0L;

	private LazyLoadTabHandler _loadHandler;

	private class ComponentListenerImpl extends ComponentAdapter
	{
		@Override
		final public void componentShown(ComponentEvent e)
		{
			removeComponentListener(this);
			if (getParent() != null)
				load();
		}
	}

	public LazilyLoadedTab(LazyLoadTabHandler handler, JComponent component)
	{
		super(new GridBagLayout());

		_loadHandler = handler;
		add(component,
			new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));

		addComponentListener(new ComponentListenerImpl());
	}

	public void load()
	{
		if (_loadHandler != null) {
			_loadHandler.load();
			_loadHandler = null;
		}
	}
}
