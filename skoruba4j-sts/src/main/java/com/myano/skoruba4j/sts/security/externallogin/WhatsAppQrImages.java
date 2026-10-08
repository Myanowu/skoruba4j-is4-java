package com.myano.skoruba4j.sts.security.externallogin;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/** PNG QR for wa.me deep links. */
public final class WhatsAppQrImages {
  private WhatsAppQrImages() {}

  public static byte[] png(String content, int size) {
    try {
      int px = size <= 0 ? 240 : size;
      BitMatrix matrix =
          new QRCodeWriter()
              .encode(
                  content,
                  BarcodeFormat.QR_CODE,
                  px,
                  px,
                  Map.of(
                      EncodeHintType.ERROR_CORRECTION,
                      ErrorCorrectionLevel.M,
                      EncodeHintType.MARGIN,
                      1,
                      EncodeHintType.CHARACTER_SET,
                      "UTF-8"));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("QR encode failed", e);
    }
  }
}
