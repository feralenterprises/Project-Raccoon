package com.feralenterprises.raccoon.core.account;

/** Tri-state membership. {@link #UNKNOWN} is never treated as {@link #MEMBER}. */
public enum Membership
{
	MEMBER,
	NON_MEMBER,
	UNKNOWN
}
