package om.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A blurred wallpaper sample, translucent tint and fine specular glass edges. */
final class FrostedGlassDrawable extends Drawable {
    interface Backdrop { Bitmap bitmap(); }
    private final View owner,scene;
    private final Backdrop backdrop;
    private final float radius,density;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF rect=new RectF();
    private final Matrix matrix=new Matrix();
    private LinearGradient sheen,edge;
    private final int[] ownerPosition=new int[2],scenePosition=new int[2];
    private Bitmap sampled;
    private BitmapShader shader;
    private boolean focused;
    FrostedGlassDrawable(View owner,View scene,Backdrop backdrop,float radius) {
        this.owner=owner;this.scene=scene;this.backdrop=backdrop;this.radius=radius;
        density=owner.getResources().getDisplayMetrics().density;
    }
    void setFocused(boolean focused){this.focused=focused;prepareGradients();invalidateSelf();}
    @Override protected void onBoundsChange(Rect bounds){super.onBoundsChange(bounds);prepareGradients();}
    private void prepareGradients() {
        Rect b=getBounds();
        sheen=new LinearGradient(0,b.top,Math.max(1,b.width()*.6f),Math.max(b.top+1,b.bottom),
            new int[]{focused?0x70FFFFFF:0x4DFFFFFF,0x15FFFFFF,0x08FFFFFF},new float[]{0,.47f,1},Shader.TileMode.CLAMP);
        edge=new LinearGradient(0,b.top,0,Math.max(b.top+1,b.bottom),focused?0xF1E6FFF8:0xB2FFFFFF,focused?0xB1D2FFF0:0x35FFFFFF,Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas c) {
        Rect b=getBounds();float inset=density*.65f;rect.set(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(0xCC233940);c.drawRoundRect(rect,radius,radius,paint);
        Bitmap blur=backdrop.bitmap();
        if(blur!=null && !blur.isRecycled() && scene.getWidth()>0 && scene.getHeight()>0) {
            if(sampled!=blur) { sampled=blur;shader=new BitmapShader(blur,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP); }
            owner.getLocationOnScreen(ownerPosition);scene.getLocationOnScreen(scenePosition);
            float scale=Math.max((float)scene.getWidth()/blur.getWidth(),(float)scene.getHeight()/blur.getHeight());
            float x=(scene.getWidth()-blur.getWidth()*scale)/2f-(ownerPosition[0]-scenePosition[0]);
            float y=(scene.getHeight()-blur.getHeight()*scale)/2f-(ownerPosition[1]-scenePosition[1]);
            matrix.setScale(scale,scale);matrix.postTranslate(x,y);shader.setLocalMatrix(matrix);
            paint.setShader(shader);paint.setColor(Color.WHITE);c.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);
        }
        paint.setColor(focused?0x67243D43:0x7010202B);c.drawRoundRect(rect,radius,radius,paint);
        if(sheen==null)prepareGradients();paint.setShader(sheen);
        c.drawRoundRect(rect,radius,radius,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density*(focused?1.65f:.8f));
        paint.setShader(edge);
        c.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void getOutline(Outline outline){outline.setRoundRect(getBounds(),radius);outline.setAlpha(.24f);}
    @Override public void setAlpha(int alpha){ }
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
