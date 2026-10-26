package om.ufuq.remote;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;

/** Local-only Android TV Remote v2 client. Uses the published Polo/Remote schemas. */
final class RemoteSession {
    interface Identity {
        SSLSocket socket(byte[] pin,boolean pairing)throws Exception;
        X509Certificate certificate()throws Exception;
    }
    interface Listener {
        void status(String value);
        void codeNeeded();
        void paired(byte[] certificatePin);
        void ready();
        void disconnected(String reason);
        void textAvailable(boolean available);
    }
    private final Identity identity;private final Listener listener;
    private final ExecutorService work=Executors.newSingleThreadExecutor(),writes=Executors.newSingleThreadExecutor();
    private final AtomicInteger epoch=new AtomicInteger();private final Object sendLock=new Object();
    private volatile SSLSocket socket;private volatile boolean ready,awaitingCode,imeAvailable;
    private volatile int imeCounter,fieldCounter,features;private String host;private int port;private X509Certificate peer;
    RemoteSession(Identity identity,Listener listener){this.identity=identity;this.listener=listener;}
    boolean isReady(){return ready;}
    boolean hasTextField(){return ready&&imeAvailable;}
    void connect(String address,int remotePort,byte[] pin){
        disconnect();host=address;port=remotePort;int run=epoch.get();listener.status(pin==null?"جارٍ طلب الاقتران…":"جارٍ الاتصال…");
        work.execute(()->{try{if(pin==null)beginPair(run);else control(run,pin);}catch(Exception error){fail(run,error);}});
    }
    private SSLSocket open(int run,int targetPort,byte[] pin,boolean pairing)throws Exception {
        SSLSocket next=identity.socket(pin,pairing);if(run!=epoch.get()){next.close();throw new IOException("Cancelled");}socket=next;
        next.setTcpNoDelay(true);next.connect(new InetSocketAddress(host,targetPort),6000);next.setSoTimeout(10000);next.startHandshake();
        if(run!=epoch.get()){next.close();throw new IOException("Cancelled");}return next;
    }
    private static byte[] envelope(int field,byte[] inner){return Wire.b().number(1,2).number(2,200).bytes(field,inner).build();}
    private Wire.Message read()throws IOException {SSLSocket current=socket;if(current==null)throw new IOException("Disconnected");return Wire.parse(Wire.readFrame(current.getInputStream()));}
    private void sendNow(byte[] bytes)throws IOException {
        synchronized(sendLock){SSLSocket current=socket;if(current==null)throw new IOException("Disconnected");Wire.writeFrame(current.getOutputStream(),bytes);}
    }
    private void expectPair(int field)throws IOException {Wire.Message message=read();if(message.number(2)!=200||!message.has(field))throw new IOException("التلفاز رفض خطوة الاقتران؛ أعد المحاولة");}
    private void beginPair(int run)throws Exception {
        SSLSocket current=open(run,port+1,null,true);peer=(X509Certificate)current.getSession().getPeerCertificates()[0];
        sendNow(envelope(10,Wire.b().text(1,"atvremote").text(2,"أُفق ريموت · M7MD929").build()));expectPair(11);
        byte[] encoding=Wire.b().number(1,3).number(2,6).build();
        sendNow(envelope(20,Wire.b().bytes(1,encoding).number(3,1).build()));expectPair(20);
        sendNow(envelope(30,Wire.b().bytes(1,encoding).number(2,1).build()));expectPair(31);
        if(run==epoch.get()){awaitingCode=true;listener.status("أدخل رمز التلفاز");listener.codeNeeded();}
    }
    void submitCode(String code){int run=epoch.get();if(!awaitingCode)return;
        work.execute(()->{try{
            byte[] proof=PairingSecret.calculate(identity.certificate().getPublicKey(),peer.getPublicKey(),code);
            sendNow(envelope(40,Wire.b().bytes(1,proof).build()));expectPair(41);awaitingCode=false;
            byte[] pin=MessageDigest.getInstance("SHA-256").digest(peer.getEncoded());
            if(run!=epoch.get())return;listener.paired(pin);closeSocket();control(run,pin);
        }catch(GeneralSecurityException badCode){if(run==epoch.get()){listener.status(badCode.getMessage());listener.codeNeeded();}}
        catch(Exception error){fail(run,error);}});
    }
    private void control(int run,byte[] pin)throws Exception {
        SSLSocket current=open(run,port,pin,false);current.setSoTimeout(20000);features=0;
        Thread reader=new Thread(()->{try{
            while(run==epoch.get()&&!current.isClosed()){
                Wire.Message message=read();if(run!=epoch.get())return;
                if(message.has(1)){
                    features=(int)message.child(1).number(1)&(1|2|4|32|64|512);
                    if((features&2)==0)throw new IOException("خدمة التلفاز لا تدعم أزرار الريموت");
                    byte[] info=Wire.b().text(1,"Ufuq Remote").text(2,"M7MD929").number(3,1).text(4,"1").text(5,"om.ufuq.remote").text(6,"1.0.0").build();
                    sendNow(Wire.b().bytes(1,Wire.b().number(1,features).bytes(2,info).build()).build());
                }
                if(message.has(2))sendNow(Wire.b().bytes(2,Wire.b().number(1,features).build()).build());
                if(message.has(8))sendNow(Wire.b().bytes(9,Wire.b().number(1,message.child(8).number(1)).build()).build());
                if(message.has(20)){imeAvailable=message.child(20).has(2);listener.textAvailable(imeAvailable);}
                if(message.has(21)){Wire.Message edit=message.child(21);imeCounter=(int)edit.number(1);fieldCounter=(int)edit.number(2);}
                if(message.has(22)){imeAvailable=true;listener.textAvailable(true);}
                if(message.has(40)&&!ready){ready=true;listener.status("متصل بالتلفاز");listener.ready();}
                if(message.has(3))throw new IOException("خدمة التلفاز أرسلت خطأ؛ أعد الاتصال");
            }
        }catch(Exception error){fail(run,error);}},"Ufuq-remote-reader");reader.setDaemon(true);reader.start();
    }
    private void queue(byte[] payload){if(!ready)return;int run=epoch.get();writes.execute(()->{try{if(run==epoch.get()&&ready)sendNow(payload);}catch(Exception error){fail(run,error);}});}
    void key(int code,int direction){queue(Wire.b().bytes(10,Wire.b().number(1,code).number(2,direction).build()).build());}
    void text(String value){
        if(!hasTextField()||value.isEmpty())return;int cursor=value.codePointCount(0,value.length())-1;
        byte[] object=Wire.b().number(1,cursor).number(2,cursor).text(3,value).build();
        byte[] edit=Wire.b().number(1,1).bytes(2,object).build();
        queue(Wire.b().bytes(21,Wire.b().number(1,imeCounter).number(2,fieldCounter).bytes(3,edit).build()).build());
    }
    void appLink(String link){if((features&512)!=0)queue(Wire.b().bytes(90,Wire.b().text(1,link).build()).build());}
    private void fail(int run,Exception error){
        if(!epoch.compareAndSet(run,run+1))return;ready=false;awaitingCode=false;imeAvailable=false;closeSocket();
        String reason=error instanceof SocketTimeoutException?"انتهت مهلة الاتصال؛ تأكد أن التلفاز يعمل وعلى الشبكة نفسها":error.getMessage();
        if(reason==null||reason.isEmpty())reason="انقطع الاتصال؛ أعد الاتصال بالتلفاز";
        listener.disconnected(reason);
    }
    private void closeSocket(){SSLSocket previous=socket;socket=null;if(previous!=null)try{previous.close();}catch(IOException ignored){}}
    void disconnect(){epoch.incrementAndGet();ready=false;awaitingCode=false;imeAvailable=false;closeSocket();}
    void shutdown(){disconnect();work.shutdownNow();writes.shutdownNow();}
}
