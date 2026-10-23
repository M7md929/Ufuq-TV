package om.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.service.dreams.DreamService;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.concurrent.*;

/** Android's native idle screensaver, using the very same offline photo catalog. */
public class OmanDreamService extends DreamService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private List<PhotoCatalog.Photo> photos;
    private PhotoCatalog.ShuffleBag bag;
    private PhotoFrame image;
    private TextView place;
    private boolean dreaming;
    private int previous=-1,generation;
    private final Runnable rotate=()->showNext();

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);setFullscreen(true);setScreenBright(true);
        photos=PhotoCatalog.load(this);bag=new PhotoCatalog.ShuffleBag(photos.size());
        FrameLayout scene=new FrameLayout(this);scene.setBackgroundColor(Color.BLACK);
        image=new PhotoFrame(this);
        scene.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x00000000,0x72000000});
        shade.setBackground(gradient);scene.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        place=new TextView(this);place.setTextColor(Color.WHITE);place.setTextSize(16);
        place.setGravity(Gravity.RIGHT);place.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        place.setShadowLayer(5,0,1,0xAA000000);
        int margin=Math.round(32*getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);
        label.setMargins(margin,margin,margin,margin);scene.addView(place,label);
        setContentView(scene);
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted();dreaming=true;showNext();
    }
    private void showNext() {
        if(!dreaming || decoder.isShutdown()) return;
        final PhotoCatalog.Photo photo=photos.get(bag.next(previous));
        previous=photos.indexOf(photo);final int request=++generation;
        decoder.execute(()-> {
            final Bitmap bitmap=PhotoCatalog.decode(this,photo.asset);
            handler.post(()-> {
                if(!dreaming || request!=generation) {if(bitmap!=null) bitmap.recycle();return;}
                if(bitmap!=null) {
                    image.setPhoto(bitmap,true);image.setContentDescription("شاشة السكون: "+photo.name);
                    place.setText(photo.name+"  ·  سلطنة عُمان");
                    android.util.Log.i("Ufuq","Screensaver photo: "+photo.asset);
                }
                long interval=getSharedPreferences("quiet-tv",MODE_PRIVATE).getLong("photo_interval_ms",120000L);
                handler.postDelayed(rotate,Math.max(30000L,interval));
            });
        });
    }
    @Override public void onDreamingStopped() {
        dreaming=false;++generation;handler.removeCallbacks(rotate);
        super.onDreamingStopped();
    }
    @Override public void onDetachedFromWindow() {
        dreaming=false;++generation;handler.removeCallbacks(rotate);
        if(image!=null) image.clear();
        super.onDetachedFromWindow();
    }
    @Override public void onDestroy() {
        dreaming=false;++generation;handler.removeCallbacksAndMessages(null);decoder.shutdownNow();
        super.onDestroy();
    }
}
