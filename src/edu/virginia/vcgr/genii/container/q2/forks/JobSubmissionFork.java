package edu.virginia.vcgr.genii.container.q2.forks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import edu.virginia.vcgr.genii.container.q2.QueueServiceImpl;
import edu.virginia.vcgr.genii.container.rfork.AbstractStreamableByteIOFactoryResourceFork;
import edu.virginia.vcgr.genii.container.rfork.ResourceForkService;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.rwx.RWXMapping;

public class JobSubmissionFork extends
		AbstractStreamableByteIOFactoryResourceFork {
	public JobSubmissionFork(ResourceForkService service, String forkPath) {
		super(service, forkPath);
	}

	@Override
	@RWXMapping(RWXCategory.EXECUTE)
	public void modifyState(InputStream source) throws IOException {
		((QueueServiceImpl) getService()).submitJobStream(source);
	}

	@Override
	@RWXMapping(RWXCategory.READ)
	public void snapshotState(OutputStream sink) throws IOException {
		// do nothing
	}
}