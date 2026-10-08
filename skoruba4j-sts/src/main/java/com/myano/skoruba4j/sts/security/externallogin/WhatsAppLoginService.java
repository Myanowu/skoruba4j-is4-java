package com.myano.skoruba4j.sts.security.externallogin;

import com.myano.skoruba4j.domain.externallogin.ExternalLoginLinkMode;
import com.myano.skoruba4j.domain.externallogin.PhoneNumbers;
import org.springframework.stereotype.Service;

/** Apply inbound WhatsApp LOGIN replies onto QR sessions. */
@Service
public final class WhatsAppLoginService {
  private final WhatsAppLoginSessionStore store;
  private final ExternalIdentityLinker linker;

  public WhatsAppLoginService(WhatsAppLoginSessionStore store, ExternalIdentityLinker linker) {
    this.store = store;
    this.linker = linker;
  }

  public void applyInbound(String phoneDigits, String messageBody) {
    var session = store.findByLoginMessage(messageBody);
    if (session.isEmpty()) {
      return;
    }
    WhatsAppLoginSession s = session.get();
    if (s.status() != WhatsAppLoginSession.Status.PENDING) {
      return;
    }
    if (s.clientId() != null
        && !s.clientId().isBlank()
        && !linker.settingsForClient(s.clientId()).whatsappEnabled()) {
      s.markFailed("external-disabled");
      return;
    }
    ExternalLoginLinkMode mode =
        s.clientId() == null || s.clientId().isBlank()
            ? ExternalLoginLinkMode.LINK_EXISTING
            : linker.settingsForClient(s.clientId()).whatsappLinkMode();
    ExternalLoginLinkResult result = linker.linkWhatsApp(phoneDigits, mode);
    if (result instanceof ExternalLoginLinkResult.Authenticated ok) {
      s.markReady(PhoneNumbers.digitsOnly(phoneDigits), ok.user().id());
    } else if (result instanceof ExternalLoginLinkResult.NeedConfirm need) {
      s.markNeedConfirm(PhoneNumbers.digitsOnly(phoneDigits), need.pending());
    } else if (result instanceof ExternalLoginLinkResult.Rejected rejected) {
      s.markFailed(rejected.messageKey());
    } else {
      s.markFailed("external");
    }
  }
}
