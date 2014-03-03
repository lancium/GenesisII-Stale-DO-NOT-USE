package edu.virginia.vcgr.genii.ui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import edu.virginia.vcgr.genii.client.asyncpost.PostProtocols;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.ui.prefs.UIPreferences;
import edu.virginia.vcgr.genii.ui.progress.ProgressMonitorFactory;
import edu.virginia.vcgr.genii.ui.rns.DirectoryChangeNexus;
import edu.virginia.vcgr.genii.ui.trash.TrashCan;

public class UIContext implements Cloneable {
	private ApplicationContext _applicationContext;
	private ExecutorService _executor;
	private ProgressMonitorFactory _progressMonitorFactory;
	private UIConfiguration _configuration;
	private UIPreferences _uiPreferences;
	private TrashCan _trashCan;
	private ICallingContext _callingContext;
	private DirectoryChangeNexus _dChangeNexus;

	private UIContext(ApplicationContext applicationContext,
			ExecutorService executor,
			ProgressMonitorFactory progressMonitorFactory,
			UIConfiguration uiConfiguration, UIPreferences uiPreferences,
			TrashCan trashCan, ICallingContext callingContext,
			DirectoryChangeNexus dChangeNexus) {
		_applicationContext = applicationContext;
		_executor = executor;
		_progressMonitorFactory = progressMonitorFactory;
		if (_progressMonitorFactory == null)
			_progressMonitorFactory = new ProgressMonitorFactory(_executor);
		_configuration = uiConfiguration;
		_callingContext = callingContext;
		_trashCan = trashCan;
		_uiPreferences = uiPreferences;
		_dChangeNexus = dChangeNexus;
	}

	public UIContext(ApplicationContext applicationContext)
			throws FileNotFoundException, IOException {
		this(applicationContext, Executors
				.newCachedThreadPool(new InternalThreadFactory()), null,
				new UIConfiguration(), new UIPreferences(), new TrashCan(),
				ContextManager.getExistingContext(), new DirectoryChangeNexus());
	}

	@Override
	final public Object clone() {
		return new UIContext(_applicationContext, _executor,
				_progressMonitorFactory, _configuration, _uiPreferences,
				_trashCan, _callingContext.deriveNewContext(), _dChangeNexus);
	}

	final public ExecutorService executor() {
		return _executor;
	}

	final public ProgressMonitorFactory progressMonitorFactory() {
		return _progressMonitorFactory;
	}

	final public ICallingContext callingContext() {
		return _callingContext;
	}

	final public OutputStream openErrorReportStream() {
		return PostProtocols.openPostProtocol(_configuration
				.errorReportTarget());
	}

	final public UIPreferences preferences() {
		return _uiPreferences;
	}

	final public TrashCan trashCan() {
		return _trashCan;
	}

	final public DirectoryChangeNexus directoryChangeNexus() {
		return _dChangeNexus;
	}

	final public ApplicationContext applicationContext() {
		return _applicationContext;
	}

	static private class InternalThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable r) {
			Thread th = new Thread(r, "UIContext Thread");
			th.setDaemon(true);

			return th;
		}
	}
}