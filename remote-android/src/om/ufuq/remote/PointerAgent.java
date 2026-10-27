package om.ufuq.remote;

import android.os.SystemClock;
import android.view.*;
import android.graphics.*;
import java.io.*;
import java.lang.reflect.*;

/** Runs ONLY as the TV's authorized ADB shell uid via app_process, never as an app service.
 * No network listener, installed package, accessibility permission or background daemon. */
public final class PointerAgent {
    private Object manager;private Method inject,display;private float x=960,y=540;private int width,height;private Cursor cursor;private volatile long lastInput=SystemClock.elapsedRealtime();
    private boolean submit(InputEvent event)throws Exception {try{if(display!=null)display.invoke(event,0);return Boolean.TRUE.equals(inject.invoke(manager,event,0));}finally{if(event instanceof MotionEvent)((MotionEvent)event).recycle();}}
    private void move(int action,int button,float scroll)throws Exception {
        MotionEvent.PointerProperties pp=new MotionEvent.PointerProperties();pp.id=0;pp.toolType=MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x;coords.y=y;coords.pressure=action==MotionEvent.ACTION_DOWN?1:0;coords.size=1;coords.setAxisValue(MotionEvent.AXIS_VSCROLL,scroll);
        long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{pp},new MotionEvent.PointerCoords[]{coords},0,button,1,1,0,0,InputDevice.SOURCE_MOUSE,0);
        if(!submit(e))throw new IOException("Input injection refused");if(cursor!=null&&action!=MotionEvent.ACTION_HOVER_EXIT)cursor.position(x,y);
    }
    private void run(int w,int h)throws Exception {
        width=w;height=h;x=w/2f;y=h/2f;
        Class<?> type;try{type=Class.forName("android.hardware.input.InputManagerGlobal");}catch(ClassNotFoundException old){type=Class.forName("android.hardware.input.InputManager");}
        manager=type.getMethod("getInstance").invoke(null);inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
        try{display=InputEvent.class.getMethod("setDisplayId",int.class);}catch(NoSuchMethodException older){}
        cursor=new Cursor(Math.max(24,Math.min(60,h/36)));
        Thread watchdog=new Thread(()->{while(true){SystemClock.sleep(2000);if(SystemClock.elapsedRealtime()-lastInput>15000)System.exit(0);}},"Ufuq-pointer-liveness");watchdog.setDaemon(true);watchdog.start();
        System.out.println("UFUQ_READY");System.out.flush();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(System.in,"UTF-8"))){String line;
            while((line=in.readLine())!=null){lastInput=SystemClock.elapsedRealtime();if(line.length()>128)break;String[] p=line.split(" ");if(p[0].equals("Q"))break;
                switch(p[0]){
                    case "P":break;
                    case "M":if(p.length!=3)throw new IOException();x=Math.max(0,Math.min(width-1,Float.parseFloat(p[1])));y=Math.max(0,Math.min(height-1,Float.parseFloat(p[2])));if(!Float.isFinite(x)||!Float.isFinite(y))throw new IOException();move(MotionEvent.ACTION_HOVER_MOVE,0,0);break;
                    case "C":move(MotionEvent.ACTION_DOWN,MotionEvent.BUTTON_PRIMARY,0);move(MotionEvent.ACTION_UP,0,0);break;
                    case "R":key(KeyEvent.KEYCODE_BACK);break;
                    case "S":if(p.length!=2)throw new IOException();float delta=Float.parseFloat(p[1]);if(!Float.isFinite(delta))throw new IOException();move(MotionEvent.ACTION_SCROLL,0,Math.max(-10,Math.min(10,delta)));break;
                    case "K":if(p.length!=2)throw new IOException();int code=Integer.parseInt(p[1]);if(code!=KeyEvent.KEYCODE_WAKEUP&&code!=KeyEvent.KEYCODE_SLEEP)throw new IOException();key(code);break;
                    default:throw new IOException("Unknown pointer action");
                }
            }
        }finally{try{move(MotionEvent.ACTION_HOVER_EXIT,0,0);}finally{cursor.close();}}
    }
    /** Shell-owned compositor surface: visible cursor without granting an app overlay permission. */
    private static final class Cursor {
        final Object control,transaction;final Class<?> controlType,txType;final Surface surface;
        Cursor(int size)throws Exception {
            controlType=Class.forName("android.view.SurfaceControl");Class<?> builderType=Class.forName("android.view.SurfaceControl$Builder");
            Constructor<?> ctor=builderType.getDeclaredConstructor();ctor.setAccessible(true);Object builder=ctor.newInstance();
            builderType.getMethod("setName",String.class).invoke(builder,"Ufuq TV pointer");builderType.getMethod("setBufferSize",int.class,int.class).invoke(builder,size+8,size+8);builderType.getMethod("setFormat",int.class).invoke(builder,PixelFormat.TRANSLUCENT);
            control=builderType.getMethod("build").invoke(builder);txType=Class.forName("android.view.SurfaceControl$Transaction");transaction=txType.getConstructor().newInstance();
            txType.getMethod("setLayerStack",controlType,int.class).invoke(transaction,control,0);txType.getMethod("setLayer",controlType,int.class).invoke(transaction,control,Integer.MAX_VALUE-1);
            Constructor<Surface> surfaceCtor=Surface.class.getDeclaredConstructor();surfaceCtor.setAccessible(true);surface=surfaceCtor.newInstance();Surface.class.getMethod("copyFrom",controlType).invoke(surface,control);
            Canvas canvas=surface.lockCanvas(null);canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);canvas.save();canvas.scale(size/36f,size/36f);Path arrow=new Path();arrow.moveTo(3,2);arrow.lineTo(3,29);arrow.lineTo(10,22);arrow.lineTo(16,35);arrow.lineTo(22,32);arrow.lineTo(15,20);arrow.lineTo(27,20);arrow.close();Paint paint=new Paint(3);paint.setColor(Color.WHITE);canvas.drawPath(arrow,paint);paint.setColor(Color.BLACK);paint.setStrokeWidth(2);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);canvas.drawPath(arrow,paint);canvas.restore();surface.unlockCanvasAndPost(canvas);
            position(-100,-100);
        }
        void position(float x,float y)throws Exception {txType.getMethod("setPosition",controlType,float.class,float.class).invoke(transaction,control,x,y);txType.getMethod("show",controlType).invoke(transaction,control);txType.getMethod("apply").invoke(transaction);}
        void close(){try{txType.getMethod("remove",controlType).invoke(transaction,control);txType.getMethod("apply").invoke(transaction);txType.getMethod("close").invoke(transaction);}catch(Exception ignored){}surface.release();try{controlType.getMethod("release").invoke(control);}catch(Exception ignored){}}
    }
    private void key(int code)throws Exception {long t=SystemClock.uptimeMillis();for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})if(!submit(new KeyEvent(t,t,action,code,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD)))throw new IOException("Key refused");}
    public static void main(String[] args){try{if(args.length!=2)throw new IOException();int w=Integer.parseInt(args[0]),h=Integer.parseInt(args[1]);if(w<100||h<100||w>16384||h>16384)throw new IOException();new PointerAgent().run(w,h);}catch(Exception error){System.out.println("UFUQ_ERROR "+error.getClass().getSimpleName());System.out.flush();}}
}
