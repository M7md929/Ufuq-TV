package om.ufuq.remote;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;

/** Small ADB client for the opted-in legacy TCP transport. Modern TLS pairing is not implied. */
final class AdbPointer {
    interface Identity {byte[] sign(byte[] token)throws Exception;byte[] publicKey()throws Exception;}
    interface Listener {void status(String value);void ready(int width,int height,String mac);void stopped(String reason);}
    private final Identity identity;private final Listener listener;private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();private final ArrayBlockingQueue<String> commands=new ArrayBlockingQueue<>(32);
    private volatile Socket socket;private volatile boolean ready;private final AtomicReference<String> movement=new AtomicReference<>();private int nextId=1,maxData=4096;
    AdbPointer(Identity identity,Listener listener){this.identity=identity;this.listener=listener;}
    boolean isReady(){return ready;}
    void connect(String host,int port,byte[] dex){
        disconnect();int run=generation.get();listener.status("جارٍ الاتصال بالماوس…");
        executor.execute(()->{try{
            Socket current=new Socket();socket=current;if(run!=generation.get()){current.close();return;}
            current.connect(new InetSocketAddress(host,port),6000);current.setTcpNoDelay(true);current.setSoTimeout(6000);
            send(AdbWire.CNXN,0x01000000,4096,"host::ufuq\0".getBytes(StandardCharsets.UTF_8));boolean signed=false,published=false;
            while(true){AdbWire.Packet p=receive();if(p.command==AdbWire.CNXN){maxData=Math.max(1024,Math.min(65536,p.b));break;}
                if(p.command!=AdbWire.AUTH||p.a!=1||p.data.length!=20)throw new IOException("هذا المنفذ ليس ADB عبر TCP؛ استخدم منفذ تصحيح الشبكة القديم");
                if(!signed){signed=true;send(AdbWire.AUTH,2,0,identity.sign(p.data));}
                else if(!published){published=true;current.setSoTimeout(60000);listener.status("وافق على تصحيح USB من الهاتف في شاشة التلفاز");send(AdbWire.AUTH,3,0,identity.publicKey());}
                else throw new IOException("رفض التلفاز تفويض الماوس");
            }
            current.setSoTimeout(10000);
            String info=shell("wm size; cat /sys/class/net/eth0/address /sys/class/net/wlan0/address 2>/dev/null");int[] size=screenSize(info);
            Matcher macPattern=Pattern.compile("(?im)^([0-9a-f]{2}(?::[0-9a-f]{2}){5})\\s*$").matcher(info);String mac="";
            while(macPattern.find()){try{WakeOnLan.packet(macPattern.group(1));mac=macPattern.group(1);break;}catch(IllegalArgumentException ignored){}}
            listener.status("جارٍ تجهيز مؤشر التلفاز…");
            push("/data/local/tmp/ufuq-pointer-1.1.0.dex",dex);
            Stream stream=open("shell:CLASSPATH=/data/local/tmp/ufuq-pointer-1.1.0.dex app_process / om.ufuq.remote.PointerAgent "+size[0]+" "+size[1]);
            StringBuilder output=new StringBuilder();long limit=System.currentTimeMillis()+12000;
            while(output.indexOf("UFUQ_READY")<0){if(System.currentTimeMillis()>limit)throw new IOException("لم يبدأ مؤشر التلفاز");output.append(new String(stream.read(),StandardCharsets.UTF_8));if(output.indexOf("UFUQ_ERROR")>=0||output.length()>16384)throw new IOException("هذا التلفاز لم يسمح بتحريك الماوس عبر ADB");}
            if(run!=generation.get())return;ready=true;listener.ready(size[0],size[1],mac);long heartbeat=System.nanoTime();
            while(run==generation.get()){
                String command=commands.poll(12,TimeUnit.MILLISECONDS),move=movement.getAndSet(null);
                if(move!=null)stream.write(move.getBytes(StandardCharsets.US_ASCII));
                if(command!=null)stream.write(command.getBytes(StandardCharsets.US_ASCII));
                if(System.nanoTime()-heartbeat>3000000000L){stream.write("P\n".getBytes(StandardCharsets.US_ASCII));heartbeat=System.nanoTime();}
                if(current.getInputStream().available()>0){AdbWire.Packet p=receive();byte[] incoming=stream.accept(p);if(incoming!=null&&incoming.length>0&&new String(incoming,StandardCharsets.UTF_8).contains("UFUQ_ERROR"))throw new IOException("توقف مؤشر التلفاز؛ أعد الاتصال");}
            }
        }catch(Exception error){if(run==generation.get()){ready=false;closeSocket();String reason=error instanceof SocketTimeoutException?"لم يستجب الماوس؛ فعّل تصحيح الشبكة ووافق على الهاتف في التلفاز":error.getMessage();listener.stopped(reason==null?"تعذّر اتصال الماوس":reason);}}
        finally{if(run==generation.get()){ready=false;closeSocket();}}});
    }
    static int[] screenSize(String value)throws IOException {
        Matcher m=Pattern.compile("(?:Physical|Override) size:\\s*(\\d+)x(\\d+)").matcher(value);int w=0,h=0;
        while(m.find()){w=Integer.parseInt(m.group(1));h=Integer.parseInt(m.group(2));}
        if(w<100||h<100||w>16384||h>16384)throw new IOException("تعذّر معرفة أبعاد شاشة التلفاز");return new int[]{w,h};
    }
    void move(float x,float y){if(ready&&Float.isFinite(x)&&Float.isFinite(y))movement.set("M "+Math.round(x)+" "+Math.round(y)+"\n");}
    void click(){command("C\n");}void back(){command("R\n");}void scroll(float value){if(Float.isFinite(value))command("S "+Math.max(-10,Math.min(10,value))+"\n");}
    void wake(){command("K 224\n");}
    private void command(String value){if(ready&&!commands.offer(value))listener.status("انتظر لحظة حتى تكتمل الأوامر");}
    private void send(int c,int a,int b,byte[] data)throws IOException {Socket s=socket;if(s==null)throw new EOFException();AdbWire.write(s.getOutputStream(),c,a,b,data);}
    private AdbWire.Packet receive()throws IOException {Socket s=socket;if(s==null)throw new EOFException();return AdbWire.read(s.getInputStream());}
    private Stream open(String destination)throws IOException {
        int local=nextId++;send(AdbWire.OPEN,local,0,(destination+"\0").getBytes(StandardCharsets.UTF_8));AdbWire.Packet p=receive();
        if(p.command!=AdbWire.OKAY||p.b!=local)throw new IOException("رفض التلفاز فتح قناة الماوس");return new Stream(local,p.a);
    }
    private final class Stream {
        final int local,remote;boolean ended;byte[] buffered;
        Stream(int local,int remote){this.local=local;this.remote=remote;}
        byte[] accept(AdbWire.Packet p)throws IOException {
            if(p.b!=local||p.a!=remote)throw new IOException("ADB stream mismatch");
            if(p.command==AdbWire.CLSE){send(AdbWire.CLSE,local,remote,new byte[0]);ended=true;throw new EOFException("انتهى اتصال مؤشر التلفاز");}
            if(p.command==AdbWire.WRTE){send(AdbWire.OKAY,local,remote,new byte[0]);return p.data;}
            if(p.command!=AdbWire.OKAY)throw new IOException("Unexpected ADB packet");return null;
        }
        void write(byte[] data)throws IOException {for(int at=0;at<data.length;){int n=Math.min(maxData,data.length-at);byte[] part=java.util.Arrays.copyOfRange(data,at,at+n);send(AdbWire.WRTE,local,remote,part);at+=n;
            while(true){AdbWire.Packet p=receive();byte[] result=accept(p);if(result!=null){if(buffered!=null)throw new IOException("Unexpected extra ADB output");buffered=result;}if(p.command==AdbWire.OKAY)break;}}}
        byte[] read()throws IOException {if(buffered!=null){byte[] result=buffered;buffered=null;return result;}while(true){byte[] result=accept(receive());if(result!=null)return result;}}
        void close()throws IOException {if(ended)return;send(AdbWire.CLSE,local,remote,new byte[0]);while(true){AdbWire.Packet p=receive();if(p.command==AdbWire.CLSE&&p.b==local){ended=true;return;}if(p.command==AdbWire.WRTE&&p.b==local)send(AdbWire.OKAY,local,remote,new byte[0]);else if(p.command!=AdbWire.OKAY)throw new IOException("ADB close mismatch");}}
    }
    private String shell(String fixedCommand)throws IOException {
        Stream stream=open("shell:"+fixedCommand);ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{while(out.size()<65536)out.write(stream.read());throw new IOException("ADB output limit");}catch(EOFException ended){if(!stream.ended)throw ended;}return out.toString("UTF-8");
    }
    private static byte[] sync(String id,int value,byte[] payload){ByteBuffer b=ByteBuffer.allocate(8+payload.length).order(ByteOrder.LITTLE_ENDIAN);b.put(id.getBytes(StandardCharsets.US_ASCII)).putInt(value).put(payload);return b.array();}
    private void push(String fixedPath,byte[] bytes)throws IOException {
        if(bytes.length>8*1024*1024)throw new IOException("Pointer payload too large");Stream stream=open("sync:");byte[] path=(fixedPath+",33188").getBytes(StandardCharsets.UTF_8);stream.write(sync("SEND",path.length,path));
        for(int at=0;at<bytes.length;at+=32768){byte[] chunk=java.util.Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+32768));stream.write(sync("DATA",chunk.length,chunk));}
        stream.write(sync("DONE",(int)(System.currentTimeMillis()/1000),new byte[0]));ByteArrayOutputStream reply=new ByteArrayOutputStream();while(reply.size()<8)reply.write(stream.read());
        if(!new String(reply.toByteArray(),0,4,StandardCharsets.US_ASCII).equals("OKAY"))throw new IOException("لم يسمح التلفاز بتجهيز الماوس");stream.write(sync("QUIT",0,new byte[0]));stream.close();
    }
    private void closeSocket(){Socket s=socket;socket=null;if(s!=null)try{s.close();}catch(IOException ignored){}}
    void disconnect(){generation.incrementAndGet();ready=false;movement.set(null);commands.clear();closeSocket();}
    void shutdown(){disconnect();executor.shutdownNow();}
}
