package edu.virginia.vcgr.genii.client.byteio;

import java.util.Calendar;
import java.util.Collection;

import javax.xml.namespace.QName;

import edu.virginia.vcgr.genii.client.rp.ResourceProperty;

/**
 * An interface that represents the resource properties (or attributes) of a random byteio resource. As per the resource property translators
 * in Genesis II, this interface does not need to be realized as a class implementation -- rather, a dynamically generated proxy is created at
 * runtime which has the ability to retrieve and translate the correct attributes.
 * 
 * @author mmm2a
 */
public interface RandomByteIORP
{
	static public final String RANDOM_BYTEIO_NS = "http://schemas.ggf.org/byteio/2005/10/random-access";

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "Size")
	public Long getSize();

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "Readable")
	public Boolean getReadable();

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "Writeable")
	public Boolean getWriteable();

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "TransferMechanism", max = "unbounded")
	public Collection<QName> getTransferMechanisms();

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "CreateTime")
	public Calendar getCreateTime();

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "ModificationTime")
	public Calendar getModificationTime();

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "ModificationTime")
	public void setModificationTime(Calendar modTime);

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "AccessTime")
	public Calendar getAccessTime();

	@ResourceProperty(namespace = RANDOM_BYTEIO_NS, localname = "AccessTime")
	public void setAccessTime(Calendar accessTime);
}