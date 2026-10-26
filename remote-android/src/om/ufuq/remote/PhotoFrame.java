package om.ufuq.remote;

import android.content.Context;
import android.graphics.*;
import android.view.Choreographer;
import android.view.View;

/** Draw both photos in one frame so TV compositors cannot drop an alpha layer. */
final class PhotoFrame extends View implements Choreographer.FrameCallback {
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix currentMatrix=new Matrix(),incomingMatrix=new Matrix();
    private Bitmap current,incoming;
    private float progress;
    private long startNanos;
    PhotoFrame(Context context){super(context);}
    void setPhoto(Bitmap bitmap,boolean animate) {
        if(bitmap==current && incoming==null)return;
        finish();
        if(!animate || current==null){current=bitmap;updateMatrices();invalidate();return;}
        incoming=bitmap;progress=0;startNanos=0;updateMatrices();
        Choreographer.getInstance().postFrameCallback(this);invalidate();
    }
    @Override public void doFrame(long frameTimeNanos) {
        if(incoming==null)return;
        if(startNanos==0)startNanos=frameTimeNanos;
        progress=Math.min(1f,(frameTimeNanos-startNanos)/280000000f);
        invalidate();
        if(progress>=1)finish();else Choreographer.getInstance().postFrameCallback(this);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(current!=null && !current.isRecycled()){paint.setAlpha(255);canvas.drawBitmap(current,currentMatrix,paint);}
        if(incoming!=null && !incoming.isRecycled()) {
            paint.setAlpha(Math.round(255*progress));canvas.drawBitmap(incoming,incomingMatrix,paint);
        }
    }
    private void updateMatrices(){crop(current,currentMatrix);crop(incoming,incomingMatrix);}
    private void crop(Bitmap bitmap,Matrix matrix) {
        if(bitmap==null)return;
        float scale=Math.max((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());
        matrix.setScale(scale,scale);
        matrix.postTranslate((getWidth()-bitmap.getWidth()*scale)/2f,(getHeight()-bitmap.getHeight()*scale)/2f);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);updateMatrices();}
    private void finish() {
        Choreographer.getInstance().removeFrameCallback(this);
        if(incoming!=null){current=incoming;incoming=null;}
        progress=0;updateMatrices();invalidate();
    }
    void clear(){finish();current=null;invalidate();}
    @Override protected void onDetachedFromWindow(){finish();super.onDetachedFromWindow();}
}
