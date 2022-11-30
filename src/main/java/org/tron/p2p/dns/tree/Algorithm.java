package org.tron.p2p.dns.tree;


import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Base64;
import org.bouncycastle.util.encoders.Base32;
import org.tron.p2p.utils.ByteArray;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.crypto.Sign.SignatureData;

public class Algorithm {

  private static int truncateLength = 26;
  private static String padding = "=";

  public static String compressPubKey(BigInteger pubKey) {
    String pubKeyYPrefix = pubKey.testBit(0) ? "03" : "02";
    String pubKeyHex = pubKey.toString(16);
    String pubKeyX = pubKeyHex.substring(0, 64);
    return pubKeyYPrefix + pubKeyX;
  }

  private static ECKeyPair generateKeyPair(String privateKey) {
    BigInteger privKey = new BigInteger(privateKey, 16);
    BigInteger pubKey = Sign.publicKeyFromPrivate(privKey);
    ECKeyPair keyPair = new ECKeyPair(privKey, pubKey);
    return keyPair;
  }

  /**
   * The produced signature is in the 65-byte [R || S || V] format where V is 0 or 1.
   */
  public static byte[] sigData(String msg, String privateKey) {
    ECKeyPair keyPair = generateKeyPair(privateKey);
    Sign.SignatureData signature = Sign.signMessage(msg.getBytes(), keyPair, true);
    byte[] data = new byte[65];
    System.arraycopy(signature.getR(), 0, data, 0, 32);
    System.arraycopy(signature.getS(), 0, data, 32, 32);
    data[64] = signature.getV()[0];
    return data;
  }

  private static BigInteger recoverPublicKey(String msg, byte[] sig) throws SignatureException {
    int recId = sig[64];
    if (recId < 27) {
      recId += 27;
    }
    Sign.SignatureData signature = new SignatureData((byte) recId, ByteArray.subArray(sig, 0, 32),
        ByteArray.subArray(sig, 32, 64));
    BigInteger pubKeyRecovered = Sign.signedMessageToKey(msg.getBytes(), signature);
    return pubKeyRecovered;
  }

  public static boolean verifySignature(String publicKey, String msg, byte[] sig)
      throws SignatureException {
    BigInteger pubKey = new BigInteger(publicKey, 16);
    BigInteger pubKeyRecovered = recoverPublicKey(msg, sig);
    return pubKey.equals(pubKeyRecovered);
  }

  public static boolean isValidHash(String base32Hash) {
    if (base32Hash == null || base32Hash.length() != truncateLength || base32Hash.contains("\r")
        || base32Hash.contains("\n")) {
      return false;
    }
    StringBuilder sb = new StringBuilder(base32Hash);
    for (int i = 0; i < 32 - truncateLength; i++) {
      sb.append(padding);
    }
    try {
      Base32.decode(sb.toString());
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  // An Encoding is a radix 64 encoding/decoding scheme, defined by a
  // 64-character alphabet. The most common encoding is the "base64"
  // encoding defined in RFC 4648 and used in MIME (RFC 2045) and PEM
  // (RFC 1421).  RFC 4648 also defines an alternate encoding, which is
  // the standard encoding with - and _ substituted for + and /.
  public static byte[] decode64(String base64Content) {
    return Base64.getUrlDecoder().decode(base64Content);
  }

  public static String encode64(byte[] content) {
    return new String(Base64.getUrlEncoder().encode(content), StandardCharsets.UTF_8);
  }

  private static String encode32(byte[] content) {
    return new String(Base32.encode(content), StandardCharsets.UTF_8);
  }

  public static String encode32AndTruncate(String content) {
    return encode32(ByteArray.subArray(Hash.sha3(content.getBytes()), 0, 16)).substring(0,
        truncateLength);
  }

  public static void main(String[] args) throws SignatureException {
    //test case 1
    String privateKey = "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291";
    String publicKey = ByteArray.toHexString(
        generateKeyPair(privateKey).getPublicKey().toByteArray());
    String puKeyCompress = compressPubKey(generateKeyPair(privateKey).getPublicKey());
    System.out.println(publicKey);
    System.out.println(puKeyCompress);
    String msg = "Message for signing";
    byte[] sig = sigData(msg, privateKey);
    System.out.println(verifySignature(publicKey, msg, sig));
    System.out.println();

    //test case 2
    String content = "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@morenodes.example.org";
    System.out.println(encode32AndTruncate(content));
    content = "enrtree-branch:2XS2367YHAXJFGLZHVAWLQD4ZY,H4FHT4B454P6UXFD7JCYQ5PWDY,MHTDO6TMUBRIA2XWG5LUDACK24";
    System.out.println(encode32AndTruncate(content));
    content = "enr:-HW4QOFzoVLaFJnNhbgMoDXPnOvcdVuj7pDpqRvh6BRDO68aVi5ZcjB3vzQRZH2IcLBGHzo8uUN3snqmgTiE56CH3AMBgmlkgnY0iXNlY3AyNTZrMaECC2_24YYkYHEgdzxlSNKQEnHhuNAbNlMlWJxrJxbAFvA";
    System.out.println(encode32AndTruncate(content));
    content = "enr:-HW4QAggRauloj2SDLtIHN1XBkvhFZ1vtf1raYQp9TBW2RD5EEawDzbtSmlXUfnaHcvwOizhVYLtr7e6vw7NAf6mTuoCgmlkgnY0iXNlY3AyNTZrMaECjrXI8TLNXU0f8cthpAMxEshUyQlK-AM0PW2wfrnacNI";
    System.out.println(encode32AndTruncate(content));
    content = "enr:-HW4QLAYqmrwllBEnzWWs7I5Ev2IAs7x_dZlbYdRdMUx5EyKHDXp7AV5CkuPGUPdvbv1_Ms1CPfhcGCvSElSosZmyoqAgmlkgnY0iXNlY3AyNTZrMaECriawHKWdDRk2xeZkrOXBQ0dfMFLHY4eENZwdufn1S1o";
    System.out.println(encode32AndTruncate(content));
    System.out.println();

    //test case 3
    System.out.println(isValidHash("C7HRFPF3BLGF3YR4DY5KX3SMBE======"));
    System.out.println(isValidHash("C7HRFPF3BLGF3YR4DY5KX3SMBE"));
    System.out.println();

    //test case 4
    String rootmsg = "enrtree-root:v1 e=VXJIDGQECCIIYNY3GZEJSFSG6U l=FDXN3SN67NA5DKA4J2GOK7BVQI seq=3447";
    byte[] sigdata = decode64(
        "1eFfi7ggzTbtAldC1pfXPn5A3mZQwEdk0-ZwCKGhZbQn2E6zWodG7v06kFu8gjiCe6FvJo04BYvgKHtPJ5pX5wE");
    System.out.println(ByteArray.toHexString(sigdata));
    System.out.println(sigdata.length);
    puKeyCompress = compressPubKey(recoverPublicKey(rootmsg, sigdata));
    System.out.println(puKeyCompress);
    //AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE===
    System.out.println(encode32(ByteArray.fromHexString(puKeyCompress)));
  }
}
