package edu.virginia.vcgr.genii.container.bes.jsdl.personality.qsub;

import edu.virginia.vcgr.genii.client.jsdl.JSDLException;
import edu.virginia.vcgr.genii.container.bes.jsdl.personality.common.CommonPosixLikeSPMDApplicationFacet;

public class QSubSPMDApplicationFacet extends CommonPosixLikeSPMDApplicationFacet
{
	@Override
	public Object createFacetUnderstanding(Object partentUnderstanding)
		throws JSDLException
	{
		return new QSubApplicationUnderstanding();
	}
}