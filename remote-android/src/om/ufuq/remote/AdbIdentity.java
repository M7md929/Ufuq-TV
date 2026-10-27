package om.ufuq.remote;
import android.security.keystore.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import javax.security.auth.x500.X500Principal;

/** Separate, non-exportable identity for the optional TV mouse connection. */
final class AdbIdentity implements AdbPointer.Identity {
    private final KeyStore store;private static final String ALIAS="ufuq.remote.mouse.adb";
    AdbIdentity()throws Exception {
        store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyPairGenerator g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            g.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=Ufuq Mouse")).build());g.generateKeyPair();
        }
    }
    public byte[] publicKey()throws Exception{return AdbWire.publicKey((RSAPublicKey)store.getCertificate(ALIAS).getPublicKey());}
    public byte[] sign(byte[] token)throws Exception {
        if(token.length!=20)throw new GeneralSecurityException("Invalid ADB token");
        byte[] prefix=new byte[]{0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        Signature s=Signature.getInstance("NONEwithRSA");s.initSign((PrivateKey)store.getKey(ALIAS,null));s.update(prefix);s.update(token);return s.sign();
    }
}
