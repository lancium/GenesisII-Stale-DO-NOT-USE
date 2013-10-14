package edu.virginia.vcgr.genii.security.rwx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RWXMappingResolver {
	Class<? extends MappingResolver> value();
}