package om.ufuq.remote;

import android.security.keystore.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/** Non-exportable client key plus TV certificate pinning after code verification. */
final class ClientIdentity implements RemoteSession.Identity {
    private static final String ALIAS="ufuq.remote.client.v2";
    private final KeyStore store;
    ClientIdentity()throws Exception {
        store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
                // Android 8–10 Conscrypt performs TLS RSA-PSS signing through a raw RSA operation.
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE,KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(new X500Principal("CN=Ufuq Remote"))
                .setCertificateSerialNumber(new java.math.BigInteger(120,new SecureRandom()))
                .setCertificateNotBefore(new Date(1577836800000L)).setCertificateNotAfter(new Date(2524608000000L)).build());
            generator.generateKeyPair();
        }
    }
    public X509Certificate certificate()throws Exception{return (X509Certificate)store.getCertificate(ALIAS);}
    public SSLSocket socket(final byte[] pinned,final boolean pairing)throws Exception {
        final X509Certificate local=certificate();final PrivateKey key=(PrivateKey)store.getKey(ALIAS,null);
        X509KeyManager keys=new X509ExtendedKeyManager(){
            public String[] getClientAliases(String type,java.security.Principal[] issuers){return type.toUpperCase(Locale.ROOT).contains("RSA")?new String[]{ALIAS}:null;}
            public String chooseClientAlias(String[] types,java.security.Principal[] issuers,Socket socket){for(String type:types)if(type.toUpperCase(Locale.ROOT).contains("RSA"))return ALIAS;return null;}
            public String[] getServerAliases(String type,java.security.Principal[] issuers){return null;}
            public String chooseServerAlias(String type,java.security.Principal[] issuers,Socket socket){return null;}
            public X509Certificate[] getCertificateChain(String alias){return ALIAS.equals(alias)?new X509Certificate[]{local}:null;}
            public PrivateKey getPrivateKey(String alias){return ALIAS.equals(alias)?key:null;}
        };
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException("Not a server");}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException {
                if(chain==null||chain.length==0)throw new CertificateException("Missing TV certificate");
                if(pairing)return; // Bootstrap only: Polo PIN proof verifies this key before it can be saved.
                try{if(pinned==null||!MessageDigest.isEqual(pinned,MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded())))
                    throw new CertificateException("شهادة التلفاز تغيّرت؛ أعد الاقتران من إعدادات الاتصال");}
                catch(GeneralSecurityException error){throw new CertificateException(error);}
            }
        };
        SSLContext context=SSLContext.getInstance("TLS");context.init(new KeyManager[]{keys},new TrustManager[]{trust},new SecureRandom());
        return (SSLSocket)context.getSocketFactory().createSocket();
    }
}
