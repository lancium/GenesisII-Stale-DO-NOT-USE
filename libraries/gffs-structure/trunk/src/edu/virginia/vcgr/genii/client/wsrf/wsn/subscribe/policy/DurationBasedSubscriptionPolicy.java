package edu.virginia.vcgr.genii.client.wsrf.wsn.subscribe.policy;

import javax.xml.bind.annotation.XmlAttribute;

import edu.virginia.vcgr.genii.client.utils.units.AxisDuration;
import edu.virginia.vcgr.genii.client.utils.units.Duration;

public abstract class DurationBasedSubscriptionPolicy extends AbstractSubscriptionPolicy
{
	static final long serialVersionUID = 0L;

	@XmlAttribute(name = "duration", required = true)
	private AxisDuration _duration = null;

	private DurationBasedSubscriptionPolicy()
	{
		super(null);
	}

	protected DurationBasedSubscriptionPolicy(SubscriptionPolicyTypes policyType, Duration duration)
	{
		super(policyType);
		_duration = new AxisDuration(duration);
	}

	final public Duration duration()
	{
		return _duration;
	}
}
