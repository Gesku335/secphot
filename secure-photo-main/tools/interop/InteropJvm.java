import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;

public final class InteropJvm {
  static final byte[] INFO = "securephoto-v1".getBytes(StandardCharsets.UTF_8);
  static final byte[] AAD = "interop-photo-0001".getBytes(StandardCharsets.UTF_8);
  static final byte[] PLAINTEXT = "fixed JPEG-like bytes: \u00ff\u00d8\u00ff\u00e0 secure-photo interop \u00ff\u00d9".getBytes(StandardCharsets.UTF_8);
  static byte[] b64(String s) { return Base64.getDecoder().decode(s); }
  static byte[] readB64(String name) throws Exception { return b64(Files.readString(Path.of(name)).trim()); }
  static byte[] hkdf(byte[] shared) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
    byte[] prk = mac.doFinal(shared);
    mac.init(new SecretKeySpec(prk, "HmacSHA256"));
    return mac.doFinal(concat(INFO, new byte[] {1}));
  }
  static byte[] concat(byte[]... xs) { int n=0; for (byte[] x:xs) n+=x.length; byte[] o=new byte[n]; int p=0; for(byte[] x:xs){System.arraycopy(x,0,o,p,x.length);p+=x.length;} return o; }
  static byte[] encrypt(PublicKey receiver, SecureRandom random) throws Exception {
    KeyPairGenerator g=KeyPairGenerator.getInstance("EC"); g.initialize(new ECGenParameterSpec("secp256r1"), random); KeyPair eph=g.generateKeyPair();
    KeyAgreement ka=KeyAgreement.getInstance("ECDH"); ka.init(eph.getPrivate()); ka.doPhase(receiver,true); byte[] keyBytes=hkdf(ka.generateSecret());
    byte[] iv=new byte[12]; random.nextBytes(iv); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keyBytes,"AES"),new GCMParameterSpec(128,iv)); c.updateAAD(AAD); byte[] ct=c.doFinal(PLAINTEXT);
    ECPublicKey ep=(ECPublicKey)eph.getPublic(); byte[] x=unsigned32(ep.getW().getAffineX().toByteArray()), y=unsigned32(ep.getW().getAffineY().toByteArray());
    byte[] raw=concat(new byte[]{4},x,y); Files.write(Path.of("java-payload.bin"),concat(raw,iv,ct)); return PLAINTEXT;
  }
  static byte[] unsigned32(byte[] x){ byte[] o=new byte[32]; int src=Math.max(0,x.length-32); int len=x.length-src; System.arraycopy(x,src,o,32-len,len); return o; }
  static byte[] decrypt(PrivateKey receiver, byte[] payload, byte[] aad) throws Exception {
    if(payload.length<93 || payload[0]!=4) throw new GeneralSecurityException("bad payload");
    byte[] xb=Arrays.copyOfRange(payload,1,33), yb=Arrays.copyOfRange(payload,33,65); ECParameterSpec params=((ECPrivateKey)receiver).getParams();
    KeyFactory kf=KeyFactory.getInstance("EC"); ECPublicKey eph=(ECPublicKey)kf.generatePublic(new ECPublicKeySpec(new ECPoint(new BigInteger(1,xb),new BigInteger(1,yb)),params));
    KeyAgreement ka=KeyAgreement.getInstance("ECDH"); ka.init(receiver); ka.doPhase(eph,true); byte[] keyBytes=hkdf(ka.generateSecret());
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keyBytes,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(payload,65,77))); c.updateAAD(aad); return c.doFinal(Arrays.copyOfRange(payload,77,payload.length));
  }
  public static void main(String[] args) throws Exception {
    PrivateKey priv=KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(readB64("receiver-private.pk8.b64")));
    PublicKey pub=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(readB64("receiver-public.spki.b64")));
    byte[] node=decrypt(priv,Files.readAllBytes(Path.of("node-payload.bin")),AAD); if(!Arrays.equals(node,Files.readAllBytes(Path.of("node-plaintext.bin")))) throw new AssertionError("Node payload mismatch");
    encrypt(pub,new SecureRandom());
    byte[] nodePayload=Files.readAllBytes(Path.of("node-payload.bin"));
    boolean badAad=false; try{decrypt(priv,nodePayload,"wrong-aad".getBytes(StandardCharsets.UTF_8));}catch(GeneralSecurityException e){badAad=true;} if(!badAad) throw new AssertionError("bad AAD accepted");
    byte[] corrupt=nodePayload.clone(); corrupt[77]^=1; boolean badCiphertext=false; try{decrypt(priv,corrupt,AAD);}catch(GeneralSecurityException e){badCiphertext=true;} if(!badCiphertext) throw new AssertionError("corrupt ciphertext accepted");
    System.out.println("JVM: decrypted Node payload, encrypted JVM payload, rejected bad AAD");
  }
}
