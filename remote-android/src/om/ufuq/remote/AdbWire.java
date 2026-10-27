package om.ufuq.remote;

import java.io.*;
import java.math.BigInteger;
import java.nio.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/** AOSP ADB 1.0 framing; a single authorized stream, never a general command UI. */
final class AdbWire {
    static final int CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43;
    static final int MAX=1024*1024;
    static final class Packet {final int command,a,b;final byte[] data;Packet(int c,int a,int b,byte[] d){command=c;this.a=a;this.b=b;data=d;}}
    static int checksum(byte[] bytes){int sum=0;for(byte b:bytes)sum+=b&255;return sum;}
    static byte[] exact(InputStream in,int size)throws IOException {if(size<0||size>MAX)throw new IOException("ADB frame too large");byte[] result=new byte[size];int at=0,n;while(at<size){n=in.read(result,at,size-at);if(n<0)throw new EOFException();if(n>0)at+=n;}return result;}
    static Packet read(InputStream in)throws IOException {
        ByteBuffer h=ByteBuffer.wrap(exact(in,24)).order(ByteOrder.LITTLE_ENDIAN);int c=h.getInt(),a=h.getInt(),b=h.getInt(),size=h.getInt(),sum=h.getInt(),magic=h.getInt();
        if(magic!=(c^0xffffffff)||size<0||size>MAX)throw new IOException("Invalid ADB header");byte[] data=exact(in,size);
        if(sum!=checksum(data))throw new IOException("Invalid ADB checksum");return new Packet(c,a,b,data);
    }
    static void write(OutputStream out,int c,int a,int b,byte[] data)throws IOException {
        if(data.length>MAX)throw new IOException("ADB frame too large");ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(c).putInt(a).putInt(b).putInt(data.length).putInt(checksum(data)).putInt(c^0xffffffff);out.write(h.array());out.write(data);out.flush();
    }
    static byte[] publicKey(RSAPublicKey key)throws IOException {
        if(key.getModulus().bitLength()!=2048)throw new IOException("ADB requires RSA 2048");
        BigInteger n=key.getModulus(),two32=BigInteger.ONE.shiftLeft(32);ByteBuffer out=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(64).putInt(n.modInverse(two32).negate().intValue());little(out,n,256);little(out,BigInteger.ONE.shiftLeft(4096).mod(n),256);out.putInt(key.getPublicExponent().intValue());
        return (Base64.getEncoder().encodeToString(out.array())+" ufuq@phone\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static void little(ByteBuffer out,BigInteger value,int bytes){for(int i=0;i<bytes;i++)out.put(value.shiftRight(i*8).byteValue());}
}
