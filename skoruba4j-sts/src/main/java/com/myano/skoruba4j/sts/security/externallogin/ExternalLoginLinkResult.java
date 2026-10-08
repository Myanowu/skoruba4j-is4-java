package com.myano.skoruba4j.sts.security.externallogin;

import com.myano.skoruba4j.domain.identity.IdentityUser;

public sealed interface ExternalLoginLinkResult
    permits ExternalLoginLinkResult.Authenticated,
        ExternalLoginLinkResult.NeedConfirm,
        ExternalLoginLinkResult.Rejected {

  record Authenticated(IdentityUser user) implements ExternalLoginLinkResult {}

  record NeedConfirm(PendingExternalLogin pending) implements ExternalLoginLinkResult {}

  record Rejected(String messageKey) implements ExternalLoginLinkResult {}
}
