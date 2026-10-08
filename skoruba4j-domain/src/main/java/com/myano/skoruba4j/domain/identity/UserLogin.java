package com.myano.skoruba4j.domain.identity;

/** External login provider row from {@code UserLogins} / {@code AspNetUserLogins}. */
public record UserLogin(String loginProvider, String providerKey, String providerDisplayName) {}
