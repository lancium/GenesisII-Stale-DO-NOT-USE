package edu.virginia.vcgr.fsii.security;

public enum PermissionBits {
	EVERYONE_EXECUTE(),
	EVERYONE_WRITE(),
	EVERYONE_READ(),
	GROUP_EXECUTE(),
	GROUP_READ(),
	GROUP_WRITE(),
	OWNER_EXECUTE(),
	OWNER_WRITE(),
	OWNER_READ()
}