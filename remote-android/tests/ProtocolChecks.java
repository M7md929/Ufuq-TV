package om.ufuq.remote;
import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
public class ProtocolChecks {
    private static int passed;
    static void expect(boolean value){if(!value)throw new AssertionError();passed++;}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt action)throws Exception {try{action.run();throw new AssertionError("Malformed input accepted");}catch(IOException|GeneralSecurityException expected){passed++;}}
    public static void main(String[] args)throws Exception {
        // The upstream schema encodes KEYCODE_HOME, SHORT as 52 04 08 03 10 03.
        byte[] home=Wire.b().bytes(10,Wire.b().number(1,3).number(2,3).build()).build();
        expect(Arrays.equals(home,new byte[]{82,4,8,3,16,3}));
        String arabic="أُفق 🇴🇲";expect(Wire.parse(Wire.b().text(1,arabic).build()).text(1).equals(arabic));
        ByteArrayOutputStream stream=new ByteArrayOutputStream();Wire.writeFrame(stream,home);Wire.writeFrame(stream,Wire.b().number(1,99999).build());
        InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(stream.toByteArray())){public int read(byte[] b,int o,int len)throws IOException{return super.read(b,o,Math.min(1,len));}};
        expect(Arrays.equals(Wire.readFrame(fragmented),home));expect(Wire.parse(Wire.readFrame(fragmented)).number(1)==99999);
        rejects(()->Wire.parse(new byte[]{0}));rejects(()->Wire.parse(new byte[]{10,5,1}));rejects(()->Wire.parse(new byte[]{11}));
        rejects(()->Wire.readFrame(new ByteArrayInputStream(new byte[]{(byte)128,(byte)128,(byte)128,1})));
        rejects(()->Wire.readFrame(new ByteArrayInputStream(new byte[]{8,1,2})));
        KeyPairGenerator keys=KeyPairGenerator.getInstance("RSA");keys.initialize(2048);KeyPair client=keys.generateKeyPair(),server=keys.generateKeyPair();
        RSAPublicKey a=(RSAPublicKey)client.getPublic(),b=(RSAPublicKey)server.getPublic();MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(java.math.BigInteger number:new java.math.BigInteger[]{a.getModulus(),a.getPublicExponent(),b.getModulus(),b.getPublicExponent()}){
            String hex=number.toString(16);if(hex.length()%2!=0)hex="0"+hex;
            for(int i=0;i<hex.length();i+=2)digest.update((byte)Integer.parseInt(hex.substring(i,i+2),16));
        }
        digest.update(new byte[]{0x42,(byte)0xB9});byte[] proof=digest.digest();String code=String.format("%02X42B9",proof[0]&255);
        expect(MessageDigest.isEqual(proof,PairingSecret.calculate(client.getPublic(),server.getPublic(),code.toLowerCase(Locale.ROOT))));
        String bad=String.format("%02X42B9",(proof[0]+1)&255);rejects(()->PairingSecret.calculate(client.getPublic(),server.getPublic(),bad));
        rejects(()->PairingSecret.calculate(client.getPublic(),server.getPublic(),"12"));rejects(()->PairingSecret.calculate(client.getPublic(),server.getPublic(),"ZZZZZZ"));
        for(int kind=0;kind<3;kind++)for(int variant=0;variant<(kind==2?4:2);variant++)for(int width:new int[]{320,411,1080}){
            int height=width*2;MonkeyMotion.Pose pose=new MonkeyMotion.Pose();
            MonkeyMotion.sample(kind,variant,0,MonkeyMotion.duration(kind)/2,width,height,pose);
            expect(Float.isFinite(pose.x)&&Float.isFinite(pose.y)&&pose.height<height*.3f);
        }
        // Independently verify wire bytes, RSA little-endian fields and malformed transport data.
        ByteArrayOutputStream adb=new ByteArrayOutputStream();AdbWire.write(adb,AdbWire.AUTH,1,0,new byte[]{1,2,(byte)255});byte[] raw=adb.toByteArray();
        expect(raw.length==27&&raw[0]=='A'&&raw[1]=='U'&&raw[2]=='T'&&raw[3]=='H'&&raw[16]==2&&raw[17]==1);
        AdbWire.Packet packet=AdbWire.read(new ByteArrayInputStream(raw));expect(packet.command==AdbWire.AUTH&&packet.a==1&&Arrays.equals(packet.data,new byte[]{1,2,(byte)255}));
        byte[] wrong=raw.clone();wrong[20]^=1;rejects(()->AdbWire.read(new ByteArrayInputStream(wrong)));byte[] corrupt=raw.clone();corrupt[26]^=1;rejects(()->AdbWire.read(new ByteArrayInputStream(corrupt)));
        rejects(()->AdbWire.read(new ByteArrayInputStream(Arrays.copyOf(raw,25))));
        byte[] tooBig=raw.clone();java.nio.ByteBuffer.wrap(tooBig).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(12,AdbWire.MAX+1);rejects(()->AdbWire.read(new ByteArrayInputStream(tooBig)));
        String publicText=new String(AdbWire.publicKey(a),java.nio.charset.StandardCharsets.US_ASCII);byte[] binary=Base64.getDecoder().decode(publicText.split(" ")[0]);
        java.nio.ByteBuffer numeric=java.nio.ByteBuffer.wrap(binary).order(java.nio.ByteOrder.LITTLE_ENDIAN);expect(binary.length==524&&numeric.getInt()==64);
        long inverse=Integer.toUnsignedLong(numeric.getInt());expect(a.getModulus().multiply(java.math.BigInteger.valueOf(inverse)).add(java.math.BigInteger.ONE).mod(java.math.BigInteger.ONE.shiftLeft(32)).signum()==0);
        byte[] modulus=new byte[256];numeric.get(modulus);for(int i=0;i<128;i++){byte tmp=modulus[i];modulus[i]=modulus[255-i];modulus[255-i]=tmp;}expect(new java.math.BigInteger(1,modulus).equals(a.getModulus()));
        byte[] rr=new byte[256];numeric.get(rr);for(int i=0;i<128;i++){byte tmp=rr[i];rr[i]=rr[255-i];rr[255-i]=tmp;}expect(new java.math.BigInteger(1,rr).equals(java.math.BigInteger.ONE.shiftLeft(4096).mod(a.getModulus())));expect(numeric.getInt()==65537&&publicText.endsWith("ufuq@phone\0"));
        byte[] magic=WakeOnLan.packet("02:11:22:33:44:55");expect(magic.length==102);for(int i=0;i<6;i++)expect(magic[i]==(byte)255);for(int i=0;i<16;i++)expect(Arrays.equals(Arrays.copyOfRange(magic,6+i*6,12+i*6),new byte[]{2,17,34,51,68,85}));
        expect(Arrays.equals(magic,WakeOnLan.packet("02-11-22-33-44-55")));
        for(String badMac:new String[]{"","0:11:22:33:44:55","GG:11:22:33:44:55","FF:FF:FF:FF:FF:FF","00:00:00:00:00:00"}){try{WakeOnLan.packet(badMac);throw new AssertionError("Invalid MAC");}catch(IllegalArgumentException expected){passed++;}}
        expect(Arrays.equals(AdbPointer.screenSize("Physical size: 1920x1080\nOverride size: 1280x720\n"),new int[]{1280,720}));rejects(()->AdbPointer.screenSize("not a TV"));rejects(()->AdbPointer.screenSize("Physical size: 999999x0"));
        System.out.println("PASS: "+passed+" Remote v2, pairing, ADB framing/RSA, Wake-on-LAN and geometry assertions");
    }
}
