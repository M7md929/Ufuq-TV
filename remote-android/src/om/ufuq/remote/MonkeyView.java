package om.ufuq.remote;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Random;

/** In-app decoration only; never draws over other apps or handles touches. */
final class MonkeyView extends View implements Choreographer.FrameCallback {
    private Bitmap atlas;private boolean playing;private long first;
    private int kind,variant;private float anchor;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect[] sprites={new Rect(19,6,663,1008),new Rect(1011,185,1524,1020)};
    private final RectF dest=new RectF();private final MonkeyMotion.Pose pose=new MonkeyMotion.Pose();
    private final Random random=new Random();
    MonkeyView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);}
    void play(){
        if(atlas==null)atlas=BitmapFactory.decodeResource(getResources(),R.raw.monkey_atlas);
        kind=random.nextInt(3);variant=random.nextInt(kind==2?4:2);anchor=kind==2?(random.nextBoolean()?1:0):.25f+random.nextFloat()*.5f;
        playing=true;first=0;Choreographer.getInstance().removeFrameCallback(this);Choreographer.getInstance().postFrameCallback(this);
    }
    void stop(){playing=false;Choreographer.getInstance().removeFrameCallback(this);invalidate();}
    public void doFrame(long nanos){
        if(!playing)return;if(first==0)first=nanos;long elapsed=(nanos-first)/1000000;
        if(elapsed>=MonkeyMotion.duration(kind)){stop();return;}
        MonkeyMotion.sample(kind,variant,anchor,elapsed,getWidth(),getHeight(),pose);
        // Keep mobile jumps compact; the corner-anchored peek retains the verified crop geometry.
        invalidate();Choreographer.getInstance().postFrameCallback(this);
    }
    protected void onDraw(Canvas canvas){if(!playing||atlas==null)return;canvas.save();
        canvas.translate(pose.x+pose.width/2,pose.y+pose.height/2);canvas.rotate(pose.angle);canvas.scale(pose.mirror?-1:1,1);
        dest.set(-pose.width/2,-pose.height/2,pose.width/2,pose.height/2);canvas.drawBitmap(atlas,sprites[pose.sprite],dest,paint);canvas.restore();}
    protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
