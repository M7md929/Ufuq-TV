package om.ufuq.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.*;
import java.io.*;
import java.util.*;

/** Offline photos shared by the home and Android's native screensaver. */
final class PhotoCatalog {
    static class Photo {
        final String asset,name;
        final int width,height;
        Photo(String asset,String name){this(asset,name,3840,2160);}
        Photo(String asset,String name,int width,int height){this.asset=asset;this.name=name;this.width=width;this.height=height;}
        boolean isUhd(){return width>=3840 && height>=2160;}
        String resolution(){return isUhd()?"4K":width+"×"+height;}
    }
    private static List<Photo> cached;
    static synchronized List<Photo> load(Context context) {
        if(cached!=null) return cached;
        ArrayList<Photo> result=new ArrayList<>();
        try(InputStream in=context.getAssets().open("photos.json")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
            while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            JSONArray entries=new JSONArray(out.toString("UTF-8"));
            for(int i=0;i<entries.length();i++) {
                JSONObject p=entries.getJSONObject(i);
                result.add(new Photo(p.getString("asset"),p.getString("name"),p.optInt("width",3840),p.optInt("height",2160)));
            }
        } catch(IOException|JSONException error) { android.util.Log.w("Ufuq","Photo library unavailable",error); }
        if(result.isEmpty()) {
            result.add(new Photo("muscat.jpg","مسقط"));
            result.add(new Photo("wadi_shab.jpg","وادي شاب"));
            result.add(new Photo("jebel_shams.jpg","جبل شمس"));
        }
        cached=Collections.unmodifiableList(result); return cached;
    }
    static Bitmap decode(Context context,String asset) {
        int sample=1;
        int targetWidth=context.getResources().getDisplayMetrics().widthPixels;
        int targetHeight=context.getResources().getDisplayMetrics().heightPixels;
        for(Photo photo:load(context)) if(photo.asset.equals(asset)) {
            while(photo.width/(sample*2)>=targetWidth && photo.height/(sample*2)>=targetHeight)sample*=2;
            break;
        }
        for(int attempt=0;attempt<2;attempt++,sample*=2) {
            try(InputStream in=context.getAssets().open(asset)) {
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inPreferredConfig=Bitmap.Config.ARGB_8888; options.inSampleSize=sample;
                Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
                if(bitmap!=null)bitmap.prepareToDraw();return bitmap;
            } catch(OutOfMemoryError error) { android.util.Log.w("Ufuq","Retrying photo with smaller decode"); }
            catch(IOException error) { android.util.Log.w("Ufuq","Photo could not load",error); return null; }
        }
        return null;
    }
    static Bitmap thumbnail(Context context,String asset) {
        try(InputStream in=context.getAssets().open("thumbnails/"+asset)) {
            return BitmapFactory.decodeStream(in);
        } catch(IOException error) {
            try(InputStream in=context.getAssets().open(asset)) {
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=8;
                return BitmapFactory.decodeStream(in,null,options);
            } catch(IOException failure) {return null;}
        }
    }
    /** Shuffle every photo once before starting a new random cycle. */
    static class ShuffleBag {
        final int count; final ArrayList<Integer> order=new ArrayList<>(); int position;
        ShuffleBag(int count){this.count=count;}
        int next(int previous) {
            if(count<=1) return 0;
            if(position>=order.size()) {
                order.clear(); for(int i=0;i<count;i++) order.add(i);
                Collections.shuffle(order); position=0;
                if(order.get(0)==previous) Collections.swap(order,0,1);
            }
            return order.get(position++);
        }
    }
    /** Small shared backdrop; three separable box passes approximate a Gaussian blur. */
    static Bitmap frost(Bitmap photo) {
        Bitmap small=Bitmap.createScaledBitmap(photo,384,216,true);
        int w=small.getWidth(),h=small.getHeight(),radius=7,divisor=radius*2+1;
        int[] src=new int[w*h],temp=new int[w*h]; small.getPixels(src,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++) {
            for(int y=0;y<h;y++) {
                int red=0,green=0,blue=0,offset=y*w;
                for(int k=-radius;k<=radius;k++) {
                    int c=src[offset+Math.max(0,Math.min(w-1,k))]; red+=(c>>16)&255;green+=(c>>8)&255;blue+=c&255;
                }
                for(int x=0;x<w;x++) {
                    temp[offset+x]=0xFF000000|((red/divisor)<<16)|((green/divisor)<<8)|(blue/divisor);
                    int add=src[offset+Math.min(w-1,x+radius+1)],remove=src[offset+Math.max(0,x-radius)];
                    red+=((add>>16)&255)-((remove>>16)&255);green+=((add>>8)&255)-((remove>>8)&255);blue+=(add&255)-(remove&255);
                }
            }
            for(int x=0;x<w;x++) {
                int red=0,green=0,blue=0;
                for(int k=-radius;k<=radius;k++) {
                    int c=temp[Math.max(0,Math.min(h-1,k))*w+x];red+=(c>>16)&255;green+=(c>>8)&255;blue+=c&255;
                }
                for(int y=0;y<h;y++) {
                    src[y*w+x]=0xFF000000|((red/divisor)<<16)|((green/divisor)<<8)|(blue/divisor);
                    int add=temp[Math.min(h-1,y+radius+1)*w+x],remove=temp[Math.max(0,y-radius)*w+x];
                    red+=((add>>16)&255)-((remove>>16)&255);green+=((add>>8)&255)-((remove>>8)&255);blue+=(add&255)-(remove&255);
                }
            }
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);result.setPixels(src,0,w,0,0,w,h);small.recycle();return result;
    }
}
