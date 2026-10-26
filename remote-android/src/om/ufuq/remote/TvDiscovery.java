package om.ufuq.remote;

import android.content.Context;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.util.*;

final class TvDiscovery {
    interface Callback{void found(String name,String host,int port);void info(String value);}
    private final NsdManager nsd;private final WifiManager wifi;private final Handler main=new Handler(Looper.getMainLooper());
    private NsdManager.DiscoveryListener listener;private WifiManager.MulticastLock lock;private int generation;
    private final ArrayDeque<NsdServiceInfo> pending=new ArrayDeque<>();private boolean resolving;
    TvDiscovery(Context context){nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);}
    void start(Callback callback){stop();final int run=generation;
        try{if(wifi!=null){lock=wifi.createMulticastLock("Ufuq discovery");lock.setReferenceCounted(false);lock.acquire();}
            listener=new NsdManager.DiscoveryListener(){
                public void onDiscoveryStarted(String type){}
                public void onServiceFound(NsdServiceInfo service){main.post(()->{if(run==generation){pending.add(service);resolve(callback,run);}});}
                public void onServiceLost(NsdServiceInfo service){}
                public void onDiscoveryStopped(String type){}
                public void onStartDiscoveryFailed(String type,int error){main.post(()->{if(run==generation){callback.info("تعذّر البحث التلقائي؛ يمكنك إدخال عنوان التلفاز");stop();}});}
                public void onStopDiscoveryFailed(String type,int error){}
            };
            nsd.discoverServices("_androidtvremote2._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);
        }catch(RuntimeException error){callback.info("البحث غير متاح؛ أدخل عنوان التلفاز يدويًا");stop();}
    }
    private void resolve(Callback callback,int run){if(resolving||pending.isEmpty()||run!=generation)return;resolving=true;
        try{nsd.resolveService(pending.remove(),new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo service,int code){main.post(()->{if(run==generation){resolving=false;resolve(callback,run);}});}
            public void onServiceResolved(NsdServiceInfo service){main.post(()->{if(run==generation){resolving=false;if(service.getHost()!=null)callback.found(service.getServiceName(),service.getHost().getHostAddress(),service.getPort());resolve(callback,run);}});}
        });}catch(RuntimeException error){resolving=false;}
    }
    void stop(){generation++;pending.clear();resolving=false;if(listener!=null)try{nsd.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}listener=null;
        if(lock!=null&&lock.isHeld())lock.release();lock=null;}
}
