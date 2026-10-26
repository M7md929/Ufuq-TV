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
        System.out.println("PASS: "+passed+" protocol framing, UTF-8, pairing-proof and portrait-motion assertions");
    }
}
