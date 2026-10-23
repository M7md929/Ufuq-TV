package om.quiet.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import java.io.IOException;
import java.util.*;

/** Optional, silent visual prank. It never takes input or wakes the display. */
public final class MonkeyService extends Service {
    static final String RANDOM="om.quiet.tv.monkey.RANDOM",DEMO="om.quiet.tv.monkey.DEMO",STOP="om.quiet.tv.monkey.STOP";
    static final String ENABLED="monkey_random";
    static final String NEXT_CHECK="monkey_next_hour_check",CHANCE="monkey_chance_percent",ATTEMPTS="monkey_attempts_per_hour";
    static volatile boolean running;
    private static final String CHANNEL="ufuq_monkey";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private SharedPreferences prefs;
    private WindowManager windows;
    private PowerManager power;
    private Bitmap atlas;
    private MonkeyView overlay;
    private boolean demo;
    private int lastKind=-1,epoch;
    private final BroadcastReceiver screenEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                handler.removeCallbacksAndMessages(null);removeOverlay();
                if(demo){if(prefs.getBoolean(ENABLED,false)){demo=false;}else stopSelf();}
            }else if(prefs.getBoolean(ENABLED,false)&&!demo)resumeHourly();
        }
    };
    static void start(Context context,String action){
        context.startForegroundService(new Intent(context,MonkeyService.class).setAction(action));
    }
    static void stop(Context context){
        context.getSharedPreferences("quiet-tv",MODE_PRIVATE).edit().putBoolean(ENABLED,false).apply();
        context.stopService(new Intent(context,MonkeyService.class));
    }
    @Override public void onCreate(){
        super.onCreate();prefs=getSharedPreferences("quiet-tv",MODE_PRIVATE);
        windows=(WindowManager)getSystemService(WINDOW_SERVICE);power=(PowerManager)getSystemService(POWER_SERVICE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"القرد المشاغب",NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);channel.enableVibration(false);channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenEvents,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?STOP:intent.getAction();
        if(STOP.equals(action)){prefs.edit().putBoolean(ENABLED,false).apply();stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){prefs.edit().putBoolean(ENABLED,false).apply();stopSelf();return START_NOT_STICKY;}
        startForeground(192,notification());running=true;
        ++epoch;handler.removeCallbacksAndMessages(null);removeOverlay();
        if(atlas==null){
            try(java.io.InputStream stream=getResources().openRawResource(R.raw.monkey_atlas)){
                atlas=BitmapFactory.decodeStream(stream);if(atlas==null)throw new IOException("Invalid monkey asset");
                atlas.prepareToDraw();
            }catch(IOException error){Log.e("UfuqMonkey","Asset unavailable",error);prefs.edit().putBoolean(ENABLED,false).apply();stopSelf();return START_NOT_STICKY;}
        }
        demo=DEMO.equals(action);
        if(demo)startDemo();
        else{prefs.edit().putBoolean(ENABLED,true).apply();resumeHourly();}
        return START_NOT_STICKY;
    }
    private Notification notification(){
        PendingIntent home=PendingIntent.getActivity(this,192,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,193,new Intent(this,MonkeyService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.app_icon)
            .setContentTitle("أُفق · القرد المشاغب").setContentText(chance()+"٪ لكل فرصة · "+attempts()+" فرصة/ساعة · الإيقاف من إعدادات أُفق")
            .setContentIntent(home).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,"إيقاف",stop).build()).build();
        n.flags|=Notification.FLAG_FOREGROUND_SERVICE;return n;
    }
    private void startDemo(){
        final int generation=epoch;
        int[][] steps={{0,0},{0,1},{1,0},{1,1},{2,0},{2,1},{2,2},{2,3},{2,0},{2,1},{2,2},{2,3}};
        for(int i=0;i<steps.length;i++){
            final int index=i,kind=steps[i][0],variant=steps[i][1];
            long delay=i<4?900+i*900:4500+(i-4)*1550;
            handler.postDelayed(()->{if(generation==epoch&&demo)play(kind,variant,index<4?.5f:index<8?.08f:.92f);},delay);
        }
        handler.postDelayed(()->{
            if(generation!=epoch)return;demo=false;removeOverlay();
            if(prefs.getBoolean(ENABLED,false))resumeHourly();else stopSelf();
        },18000);
    }
    private void scheduleRandom(long delay){
        handler.removeCallbacks(randomEvent);handler.postDelayed(randomEvent,delay);
    }
    private void resumeHourly(){
        long now=System.currentTimeMillis(),due=MonkeySchedule.nextDue(now,prefs.getLong(NEXT_CHECK,0),interval());
        prefs.edit().putLong(NEXT_CHECK,due).apply();scheduleRandom(due-now);
    }
    private int chance(){return MonkeySchedule.chance(prefs.getInt(CHANCE,MonkeySchedule.DEFAULT_CHANCE_PERCENT));}
    private int attempts(){return MonkeySchedule.attempts(prefs.getInt(ATTEMPTS,MonkeySchedule.DEFAULT_ATTEMPTS));}
    private long interval(){return MonkeySchedule.interval(attempts());}
    private final Runnable randomEvent=()->{
        if(!prefs.getBoolean(ENABLED,false)){stopSelf();return;}
        if(!power.isInteractive())return;
        long now=System.currentTimeMillis(),due=prefs.getLong(NEXT_CHECK,0);
        if(now<due){scheduleRandom(due-now);return;}
        prefs.edit().putLong(NEXT_CHECK,now+interval()).apply();
        if(MonkeySchedule.shouldAppear(random.nextInt(100),chance())){
            int kind;do{kind=random.nextInt(3);}while(kind==lastKind);lastKind=kind;
            int variant=random.nextInt(kind==MonkeyMotion.PEEK?4:2);
            float anchor=kind==MonkeyMotion.PEEK?(random.nextBoolean()?0f:1f):.22f+random.nextFloat()*.56f;
            play(kind,variant,anchor);
        }
        scheduleRandom(interval());
    };
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        writer.println("monkeyMode="+(demo?"demo":"configured"));
        writer.println("enabled="+prefs.getBoolean(ENABLED,false));
        writer.println("chancePercent="+chance());
        writer.println("attemptsPerHour="+attempts());
        writer.println("checkIntervalMillis="+interval());
        writer.println("nextCheckInMillis="+Math.max(0,prefs.getLong(NEXT_CHECK,0)-System.currentTimeMillis()));
    }
    private void play(int kind,int variant,float anchor){
        removeOverlay();if(!power.isInteractive())return;
        try{
            overlay=new MonkeyView(kind,variant,anchor);
            WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
            params.gravity=Gravity.TOP|Gravity.LEFT;params.setTitle("UfuqMonkeyOverlay");
            windows.addView(overlay,params);overlay.begin();
            Log.i("UfuqMonkey","play kind="+kind+" variant="+variant+" anchor="+anchor);
        }catch(RuntimeException error){
            Log.e("UfuqMonkey","Overlay unavailable",error);prefs.edit().putBoolean(ENABLED,false).apply();removeOverlay();stopSelf();
        }
    }
    private void removeOverlay(){
        if(overlay!=null){MonkeyView old=overlay;overlay=null;old.end();
            try{windows.removeViewImmediate(old);}catch(IllegalArgumentException ignored){}
        }
    }
    @Override public void onDestroy(){
        ++epoch;handler.removeCallbacksAndMessages(null);removeOverlay();unregisterReceiver(screenEvents);
        running=false;stopForeground(true);atlas=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private final class MonkeyView extends View implements Choreographer.FrameCallback {
        private final int kind,variant;private final float anchor;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Rect[] sprites={new Rect(19,6,663,1008),new Rect(1011,185,1524,1020)};
        private final RectF destination=new RectF();private final MonkeyMotion.Pose pose=new MonkeyMotion.Pose();
        private long firstFrame;private boolean playing;
        MonkeyView(int kind,int variant,float anchor){super(MonkeyService.this);this.kind=kind;this.variant=variant;this.anchor=anchor;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void begin(){playing=true;firstFrame=0;Choreographer.getInstance().postFrameCallback(this);}
        void end(){playing=false;Choreographer.getInstance().removeFrameCallback(this);}
        @Override public void doFrame(long nanos){
            if(!playing)return;if(firstFrame==0)firstFrame=nanos;long elapsed=(nanos-firstFrame)/1000000;
            if(elapsed>=MonkeyMotion.duration(kind)){removeOverlay();return;}
            MonkeyMotion.sample(kind,variant,anchor,elapsed,getWidth(),getHeight(),pose);
            invalidate();Choreographer.getInstance().postFrameCallback(this);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(atlas==null||!playing)return;
            canvas.save();canvas.translate(pose.x+pose.width/2,pose.y+pose.height/2);canvas.rotate(pose.angle);
            canvas.scale(pose.mirror?-1:1,1);destination.set(-pose.width/2,-pose.height/2,pose.width/2,pose.height/2);
            canvas.drawBitmap(atlas,sprites[pose.sprite],destination,paint);canvas.restore();
        }
        @Override protected void onDetachedFromWindow(){end();super.onDetachedFromWindow();}
    }
}
