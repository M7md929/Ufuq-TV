package om.ufuq.remote;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

/** Android TV Polo pairing proof. No PIN is stored or logged. */
final class PairingSecret {
    static byte[] unsigned(BigInteger value){byte[] bytes=value.toByteArray();return bytes.length>1&&bytes[0]==0?Arrays.copyOfRange(bytes,1,bytes.length):bytes;}
    static byte[] calculate(PublicKey client,PublicKey server,String code)throws GeneralSecurityException {
        String pin=code.replace(" ","").toUpperCase(Locale.ROOT);
        if(!pin.matches("[0-9A-F]{6}"))throw new GeneralSecurityException("اكتب رمزًا من ست خانات كما يظهر على التلفاز");
        if(!(client instanceof RSAPublicKey)||!(server instanceof RSAPublicKey))throw new GeneralSecurityException("شهادة التلفاز غير متوافقة");
        RSAPublicKey a=(RSAPublicKey)client,b=(RSAPublicKey)server;
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        hash.update(unsigned(a.getModulus()));hash.update(unsigned(a.getPublicExponent()));
        hash.update(unsigned(b.getModulus()));hash.update(unsigned(b.getPublicExponent()));
        hash.update((byte)Integer.parseInt(pin.substring(2,4),16));hash.update((byte)Integer.parseInt(pin.substring(4,6),16));
        byte[] proof=hash.digest();if((proof[0]&255)!=Integer.parseInt(pin.substring(0,2),16))throw new GeneralSecurityException("الرمز غير صحيح؛ راجع شاشة التلفاز");return proof;
    }
}
