package om.quiet.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** A small native TV home. No advertising, analytics or WebView. */
public class MainActivity extends Activity {
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final int WHITE = Color.rgb(250,251,250), MUTED = Color.rgb(207,218,218);
    private static final int ACCENT = Color.rgb(183,233,218);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService photoWorker=Executors.newSingleThreadExecutor();
    private Future<?> pendingPhoto;
    private boolean appsDirty=true;
    private int decodedPhoto=-1,loadingPhoto=-1;
    private final BroadcastReceiver packageChanges=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            appsDirty=true;
            if(active && openDialogs.isEmpty()){refreshApps();buildHome();}
        }
    };
    private final ExecutorService thumbnailWorker=Executors.newFixedThreadPool(2);
    private final android.util.LruCache<String,Bitmap> thumbnails=new android.util.LruCache<String,Bitmap>(12*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private final ArrayList<AlertDialog> openDialogs=new ArrayList<>();
    private SharedPreferences prefs;
    private String pendingMonkeyAction;
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<String> favorites = new ArrayList<>();
    private FrameLayout root;
    private PhotoFrame wallpaper;
    private Bitmap wallpaperBitmap;
    private Bitmap wallpaperBlur;
    private TextView clock, date, temperature, weatherText, cityText, placeText, updateText;
    private Glyph weatherGlyph;
    private View firstCard;
    private float scale = 1f;
    private boolean active, weatherBusy;
    private int photoRequest, weatherRequest;
    private long lastAttempt;
    private String focusKey = "";
    private String[] photoFiles,photoNames;
    private java.util.List<PhotoCatalog.Photo> photoCatalog;
    private int uhdPhotoCount;
    private PhotoCatalog.ShuffleBag photoBag;
    private int randomPhoto=-1;
    private long lastPhotoChange;
    private final Runnable photoRotate=()-> {if(active) loadWallpaper();};
    private final Runnable tick = new Runnable() {
        public void run() {
            updateClockAndWeather();
            if (active) { maybeWeather(false); handler.postDelayed(this, 30000); }
        }
    };

    static class App {
        String pkg, name;
        ComponentName component;
        Drawable icon;
        boolean installed = true;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("quiet-tv", MODE_PRIVATE);
        java.util.List<PhotoCatalog.Photo> photos=PhotoCatalog.load(this);
        photoCatalog=photos;uhdPhotoCount=0;
        photoFiles=new String[photos.size()];photoNames=new String[photos.size()];
        for(int i=0;i<photos.size();i++){photoFiles[i]=photos.get(i).asset;photoNames[i]=photos.get(i).name;if(photos.get(i).isUhd())uhdPhotoCount++;}
        photoBag=new PhotoCatalog.ShuffleBag(photos.size());
        if(!prefs.contains("random_photos")) prefs.edit().putBoolean("random_photos",true).apply();
        // Ibri is an explicitly approximate reading for Al Hajar; no inferred GPS fix.
        if (!prefs.contains("latitude")) prefs.edit().putString("latitude","23.22573")
            .putString("longitude","56.51572").putString("city","عبري")
            .putString("location_note","قراءة تقريبية للهجر").apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        IntentFilter packages=new IntentFilter();packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        registerReceiver(packageChanges,packages);
        immersive();
        if(root==null || appsDirty){refreshApps();buildHome();}
        else {updateClockAndWeather();loadWallpaper();}
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        immersive();
        if(root==null || appsDirty){refreshApps();buildHome();}
        else {updateClockAndWeather();loadWallpaper();}
        handler.removeCallbacks(tick);
        handler.post(tick);
        maybeWeather(false);
        if(prefs.getBoolean(MonkeyService.ENABLED,false)&&Settings.canDrawOverlays(this)&&!MonkeyService.running)
            MonkeyService.start(this,MonkeyService.RANDOM);
    }

    @Override protected void onPause() {
        active = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(photoRotate);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        ++photoRequest;photoWorker.shutdownNow();
        unregisterReceiver(packageChanges);
        thumbnailWorker.shutdownNow();thumbnails.evictAll();
        for(AlertDialog dialog:new ArrayList<>(openDialogs)) dialog.dismiss();
        super.onDestroy();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(float n) { return Math.round(n * scale * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return v;
    }
    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setClipChildren(false); v.setClipToPadding(false); return v;
    }
    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(color);
        t.setTextSize(size * scale); t.setFontFeatureSettings("kern");
        t.setIncludeFontPadding(false); t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        t.setShadowLayer(dp(4),0,dp(1),0x70000000);
        t.setFocusable(false); return t;
    }
    private void gap(LinearLayout parent, int height) {
        View v = new View(this); parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    private GradientDrawable rounded(int color, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1.5f),stroke); return g;
    }
    private void focusable(View v, String key, boolean card) {
        focusable(v,key,card,18);
    }
    private void focusable(View v,String key,boolean card,float cornerRadius) {
        v.setId(View.generateViewId()); v.setTag(key); v.setFocusable(true); v.setClickable(true);
        FrostedGlassDrawable glass=new FrostedGlassDrawable(v,root,()->wallpaperBlur,dp(cornerRadius));
        v.setBackground(glass);v.setElevation(dp(2));
        v.setOnFocusChangeListener((view,focused) -> {
            glass.setFocused(focused);
            view.setElevation(focused ? dp(8) : dp(2));
            view.animate().cancel();float zoom=focused?(card?1.035f:1.06f):1;
            view.animate().scaleX(zoom).scaleY(zoom).setDuration(70)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            if (focused) focusKey = key;
        });
    }

    private void buildHome() {
        if (isFinishing() || isDestroyed()) return;
        final String previousFocus=focusKey;
        float heightDp = getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density;
        scale = Math.min(1.35f,Math.max(0.65f,heightDp/540f));
        root = new FrameLayout(this); root.setBackgroundColor(Color.rgb(13,23,27));
        wallpaper = new PhotoFrame(this);
        if (wallpaperBitmap != null) {wallpaper.setPhoto(wallpaperBitmap,false);if(decodedPhoto>=0)wallpaper.setContentDescription("خلفية: "+photoNames[decodedPhoto]);}
        root.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        root.addView(new Shade(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content = column(); content.setClipChildren(false); content.setClipToPadding(false);
        content.setPadding(dp(48),dp(30),dp(48),dp(22));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=row();header.setGravity(Gravity.TOP|Gravity.RIGHT);
        LinearLayout statusArea=column();
        TextView signature=text("M7MD929 · \u2067أُفق\u2069 "+versionName(),10,MUTED);
        signature.setTextDirection(View.TEXT_DIRECTION_LTR);statusArea.addView(signature);gap(statusArea,6);
        LinearLayout weatherBox=row();weatherBox.setPadding(dp(16),dp(12),dp(16),dp(12));
        focusable(weatherBox,"weather",false);
        weatherBox.setContentDescription("الساعة والطقس");
        LinearLayout timeBox=column();
        clock=text("",36,WHITE);clock.setTypeface(Typeface.create("sans-serif-light",0));timeBox.addView(clock);
        date=text("",12,MUTED);date.setSingleLine(true);gap(timeBox,6);timeBox.addView(date);
        weatherBox.addView(timeBox,new LinearLayout.LayoutParams(dp(132),-2));
        View statusGap=new View(this);weatherBox.addView(statusGap,new LinearLayout.LayoutParams(dp(16),1));
        LinearLayout weatherDetails=column();
        LinearLayout reading = row();
        temperature = text("—°",26,WHITE); temperature.setTypeface(Typeface.create("sans-serif-light",0));
        weatherGlyph = new Glyph(this,"sun");
        reading.addView(weatherGlyph,new LinearLayout.LayoutParams(dp(34),dp(40)));
        LinearLayout.LayoutParams temperatureLp = new LinearLayout.LayoutParams(-2,-2); temperatureLp.setMargins(dp(8),0,dp(8),0);
        reading.addView(temperature,temperatureLp);
        weatherDetails.addView(reading);
        cityText = text("",14,WHITE); cityText.setGravity(Gravity.CENTER); weatherDetails.addView(cityText);
        weatherText = text("",12,MUTED); weatherText.setGravity(Gravity.CENTER);weatherText.setSingleLine(true);weatherText.setEllipsize(TextUtils.TruncateAt.END);
        gap(weatherDetails,3);weatherDetails.addView(weatherText);
        updateText = text("",10,MUTED); updateText.setGravity(Gravity.CENTER);gap(weatherDetails,3);weatherDetails.addView(updateText);
        weatherBox.addView(weatherDetails,new LinearLayout.LayoutParams(dp(120),-2));
        weatherBox.setOnClickListener(v -> showWeatherMenu());
        statusArea.addView(weatherBox,new LinearLayout.LayoutParams(dp(300),-2));
        header.addView(statusArea,new LinearLayout.LayoutParams(dp(300),-2));
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(142)));

        View space = new View(this); content.addView(space,new LinearLayout.LayoutParams(1,0,1));
        LinearLayout lower=new LinearLayout(this);lower.setOrientation(LinearLayout.HORIZONTAL);
        lower.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);lower.setGravity(Gravity.BOTTOM);
        lower.setClipChildren(false);lower.setClipToPadding(false);
        LinearLayout tools=column();tools.setClipChildren(false);tools.setClipToPadding(false);
        tools.setGravity(Gravity.CENTER_HORIZONTAL);tools.setPadding(dp(9),dp(9),dp(9),dp(9));
        tools.setBackground(new FrostedGlassDrawable(tools,root,()->wallpaperBlur,dp(17)));
        View inputsButton=squareAction("المداخل","input","inputs",()->showInputs());
        View settingsButton=squareAction("الإعدادات","gear","settings",()->showSettings());
        View customizeButton=squareAction("التخصيص","tune","customize",()->showFavoriteSettings());
        View wallpaperButton=squareAction("الخلفية","photo","wallpapers",()->showWallpapers());
        View allAppsButton=squareAction("كل\nالتطبيقات","apps","apps",()->showAllApps());
        View addButton=squareAction("إضافة\nتطبيق","plus","add",()->chooseFavorites());
        addButton.setContentDescription("إضافة تطبيق إلى المفضلة");
        View[] toolButtons={inputsButton,settingsButton,customizeButton,wallpaperButton,allAppsButton,addButton};
        for(int i=0;i<toolButtons.length;i++){
            if(i>0)gap(tools,6);tools.addView(toolButtons[i],new LinearLayout.LayoutParams(dp(34),dp(34)));
        }
        LinearLayout.LayoutParams toolsLp=new LinearLayout.LayoutParams(dp(52),-2);toolsLp.setMargins(0,0,dp(28),dp(5));
        lower.addView(tools,toolsLp);
        LinearLayout appArea=column();appArea.setClipChildren(false);appArea.setClipToPadding(false);

        HorizontalScrollView scroller = new HorizontalScrollView(this); scroller.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroller.setHorizontalScrollBarEnabled(false); scroller.setClipChildren(false); scroller.setClipToPadding(false);
        scroller.setFillViewport(true);
        LinearLayout cards = row(); cards.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); cards.setPadding(dp(5),dp(5),dp(5),dp(5));
        firstCard = null;
        View lastCard=null;
        for (String pkg : favorites) {
            App app = findApp(pkg); if (app == null) continue;
            View tile = appCard(app, true);
            tile.setNextFocusUpId(weatherBox.getId());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120),dp(120)); lp.setMarginEnd(dp(12));
            cards.addView(tile,lp); if (firstCard == null) firstCard = tile;
            lastCard=tile;
        }
        if(firstCard==null)firstCard=allAppsButton;
        scroller.addView(cards,new HorizontalScrollView.LayoutParams(-2,-1));
        appArea.addView(scroller,new LinearLayout.LayoutParams(-1,dp(134)));
        lower.addView(appArea,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(lower,new LinearLayout.LayoutParams(-1,-2));gap(content,14);
        for(int i=0;i<toolButtons.length;i++){
            View button=toolButtons[i];button.setNextFocusUpId(i==0?weatherBox.getId():toolButtons[i-1].getId());
            button.setNextFocusDownId(i==toolButtons.length-1?button.getId():toolButtons[i+1].getId());
            button.setNextFocusLeftId(button.getId());button.setNextFocusRightId(lastCard==null?weatherBox.getId():lastCard.getId());
        }
        weatherBox.setNextFocusDownId(firstCard.getId());weatherBox.setNextFocusLeftId(inputsButton.getId());
        if(lastCard!=null)lastCard.setNextFocusLeftId(allAppsButton.getId());

        LinearLayout footer = row();
        TextView hint = text("ضغط مطوّل على التطبيق لترتيبه أو إزالته",11,MUTED);
        footer.addView(hint,new LinearLayout.LayoutParams(0,-2,1));
        placeText = text("",12,WHITE); footer.addView(placeText);
        content.addView(footer,new LinearLayout.LayoutParams(-1,dp(20)));
        setContentView(root); updateClockAndWeather(); loadWallpaper();
        root.post(() -> {
            scroller.fullScroll(View.FOCUS_RIGHT);
            View remembered = root.findViewWithTag(previousFocus);
            if (remembered != null && remembered.isFocusable()) remembered.requestFocusFromTouch();
            else if (firstCard != null) firstCard.requestFocusFromTouch();
        });
    }

    private View squareAction(String label,String glyph,String key,Runnable action) {
        LinearLayout b=column();b.setGravity(Gravity.CENTER);focusable(b,key,false,9);
        boolean twoLines=label.contains("\n");int iconSize=twoLines?12:14;
        b.addView(new Glyph(this,glyph),new LinearLayout.LayoutParams(dp(iconSize),dp(iconSize)));gap(b,twoLines?1:3);
        TextView title=text(label,twoLines?6.8f:7.5f,WHITE);title.setGravity(Gravity.CENTER);title.setMaxLines(twoLines?2:1);b.addView(title);
        b.setContentDescription(label.replace('\n',' ')); b.setOnClickListener(v -> action.run());
        return b;
    }

    private LinearLayout appTile(View artwork,String name,int color) {
        LinearLayout tile = column();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(14),dp(14),dp(14),dp(14));
        FrameLayout iconSlot=new FrameLayout(this);
        iconSlot.addView(artwork,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        tile.addView(iconSlot,new LinearLayout.LayoutParams(dp(56),dp(52)));
        gap(tile,12);
        TextView label = text(name,14,color); label.setGravity(Gravity.CENTER); label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label,new LinearLayout.LayoutParams(-1,-2));
        return tile;
    }
    private View appUtilityCard(String label,String glyph,String key,Runnable action) {
        LinearLayout tile=appTile(new Glyph(this,glyph),label,WHITE);focusable(tile,key,true);
        tile.setContentDescription(label);tile.setOnClickListener(v->action.run());return tile;
    }
    private View appCard(App app,boolean favorite) {
        View artwork;
        if(app.pkg.equals(YOUTUBE)) artwork=new Glyph(this,"youtube");
        else if(app.icon!=null) {
            ImageView icon=new ImageView(this);icon.setImageDrawable(app.icon);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            artwork=icon;
        } else artwork=new Glyph(this,"apps");
        LinearLayout tile=appTile(artwork,app.name,WHITE);focusable(tile,"app:"+app.pkg,true);
        tile.setContentDescription(app.name + (app.installed ? "":"، غير مثبت"));
        tile.setOnClickListener(v -> launch(app));
        if (favorite) {
            final boolean[] handled = {false};
            tile.setOnLongClickListener(v -> { handled[0]=true; v.setPressed(false); showAppOptions(app); return true; });
            // Some TV remotes report long holds as repeated key-down events.
            tile.setOnKeyListener((v,key,event) -> {
                if(key!=KeyEvent.KEYCODE_DPAD_CENTER && key!=KeyEvent.KEYCODE_ENTER && key!=KeyEvent.KEYCODE_BUTTON_A) return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN) {
                    if(event.getRepeatCount()==0) { handled[0]=false; return false; }
                    if(!handled[0]) { v.cancelLongPress(); v.performLongClick(); }
                    return true;
                }
                if(event.getAction()==KeyEvent.ACTION_UP && handled[0]) { handled[0]=false; v.setPressed(false); return true; }
                return false;
            });
        }
        return tile;
    }

    private void refreshApps() {
        if(!appsDirty && !apps.isEmpty())return;
        PackageManager pm = getPackageManager(); LinkedHashMap<String,App> map = new LinkedHashMap<>();
        HashSet<String> homePackages = new HashSet<>();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : pm.queryIntentActivities(homeIntent,0)) homePackages.add(r.activityInfo.packageName);
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || homePackages.contains(pkg) || !r.activityInfo.exported || map.containsKey(pkg)) continue;
                App a = new App(); a.pkg=pkg; a.name=r.loadLabel(pm).toString();
                a.component = new ComponentName(pkg,r.activityInfo.name);
                try { a.icon=r.loadIcon(pm); } catch (Exception ignored) {}
                map.put(pkg,a);
            }
        }
        if (!map.containsKey(YOUTUBE)) {
            App a = new App(); a.pkg=YOUTUBE; a.name="YouTube"; a.installed=false; map.put(YOUTUBE,a);
        }
        apps.clear(); apps.addAll(map.values());
        apps.sort((a,b)->a.name.compareToIgnoreCase(b.name));
        favorites.clear();
        try {
            JSONArray stored = new JSONArray(prefs.getString("favorites","[\""+YOUTUBE+"\"]"));
            for(int i=0;i<stored.length();i++) {
                String pkg=stored.getString(i); if(findApp(pkg)!=null && !favorites.contains(pkg) && favorites.size()<8) favorites.add(pkg);
            }
        } catch(JSONException e) { favorites.add(YOUTUBE); }
        appsDirty=false;
    }
    private App findApp(String pkg) { for(App a:apps) if(a.pkg.equals(pkg)) return a; return null; }
    private void saveFavorites() { prefs.edit().putString("favorites",new JSONArray(favorites).toString()).apply(); buildHome(); }
    private void launch(App app) {
        if (!app.installed) {
            dialog(new AlertDialog.Builder(this).setTitle("يوتيوب غير مثبت")
                .setMessage("هل تريد فتح صفحة يوتيوب في متجر التطبيقات؟")
                .setPositiveButton("فتح المتجر",(d,w)->safeStart(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+YOUTUBE))))
                .setNegativeButton("رجوع",null)); return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!safeStart(i)) { refreshApps(); buildHome(); }
    }
    private boolean safeStart(Intent i) {
        try { startActivity(i); return true; }
        catch (ActivityNotFoundException | SecurityException e) { toast("هذا الخيار غير متاح على الجهاز"); return false; }
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private AlertDialog dialog(AlertDialog.Builder b) {
        AlertDialog d=b.create();d.create();
            Window window=d.getWindow();
            if(window!=null) {
                View decor=window.getDecorView();decor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                window.setBackgroundDrawable(new FrostedGlassDrawable(decor,root,()->wallpaperBlur,dp(24)));
                decor.setClipToOutline(true);
                WindowManager.LayoutParams attributes=window.getAttributes();attributes.dimAmount=.28f;window.setAttributes(attributes);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                for(String panel:new String[]{"parentPanel","topPanel","contentPanel","customPanel","buttonPanel","custom","scrollView"}) {
                    int id=getResources().getIdentifier(panel,"id","android");View view=decor.findViewById(id);
                    if(view!=null) view.setBackgroundColor(Color.TRANSPARENT);
                }
            }
            ListView list=d.getListView();
            if(list!=null) {
                android.util.SparseBooleanArray checked=list.getCheckedItemPositions();
                final android.util.SparseBooleanArray saved=checked==null?null:checked.clone();
                int selection=list.getSelectedItemPosition();final ListAdapter source=list.getAdapter();
                list.setAdapter(new BaseAdapter() {
                    public int getCount(){return source.getCount();}
                    public Object getItem(int position){return source.getItem(position);}
                    public long getItemId(int position){return source.getItemId(position);}
                    public boolean hasStableIds(){return source.hasStableIds();}
                    public boolean areAllItemsEnabled(){return source.areAllItemsEnabled();}
                    public boolean isEnabled(int position){return source.isEnabled(position);}
                    public int getItemViewType(int position){return source.getItemViewType(position);}
                    public int getViewTypeCount(){return source.getViewTypeCount();}
                    public View getView(int position,View recycled,ViewGroup parent) {
                        View row=source.getView(position,recycled,parent);row.setBackgroundColor(Color.TRANSPARENT);
                        styleMenuText(row);row.setMinimumHeight(dp(52));return row;
                    }
                });
                if(saved!=null) for(int i=0;i<saved.size();i++) list.setItemChecked(saved.keyAt(i),saved.valueAt(i));
                list.setSelection(Math.max(0,selection));
                list.setBackgroundColor(Color.TRANSPARENT);
                FrostedGlassDrawable selector=new FrostedGlassDrawable(list,root,()->wallpaperBlur,dp(12));selector.setFocused(true);
                list.setSelector(selector);list.setPadding(dp(12),dp(4),dp(12),dp(8));
                list.setDivider(new android.graphics.drawable.ColorDrawable(0x15FFFFFF));list.setDividerHeight(dp(1));
                list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> parent,View view,int position,long id){animateSelection(view);}
                    public void onNothingSelected(AdapterView<?> parent){}
                });
            }
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
                Button button=d.getButton(which);if(button==null || button.getVisibility()!=View.VISIBLE) continue;
                button.setTextColor(WHITE);button.setTextSize(14*scale);button.setAllCaps(false);
                button.setPadding(dp(16),dp(8),dp(16),dp(8));button.setMinHeight(dp(40));
                FrostedGlassDrawable glass=new FrostedGlassDrawable(button,root,()->wallpaperBlur,dp(12));button.setBackground(glass);
                button.setOnFocusChangeListener((view,focused)->glass.setFocused(focused));
            }
        if(d.getWindow()!=null) {
            int width=Math.min(dp(620),getResources().getDisplayMetrics().widthPixels-dp(80));
            int maxHeight=getResources().getDisplayMetrics().heightPixels-dp(48);
            View decor=d.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST));
            d.getWindow().setLayout(width,Math.min(maxHeight,decor.getMeasuredHeight()));
        }
        d.setOnDismissListener(x->openDialogs.remove(d));openDialogs.add(d);d.show();
        return d;
    }
    private void animateSelection(View view) {
        if(view==null)return;view.animate().cancel();view.setAlpha(.78f);
        view.animate().alpha(1).setDuration(70).start();
    }
    private void styleMenuText(View view) {
        if(view instanceof TextView) {
            TextView text=(TextView)view;text.setTextColor(WHITE);text.setTextSize(16*scale);
            if(text instanceof CheckedTextView) ((CheckedTextView)text).setCheckMarkTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xDDE5EEEE}));
        }
        if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++) styleMenuText(group.getChildAt(i));}
    }
    private void showFavoriteSettings() {
        dialog(new AlertDialog.Builder(this).setTitle("تخصيص التطبيقات")
            .setItems(new String[]{"اختيار التطبيقات المفضلة","ترتيب التطبيقات أو إزالة تطبيق"},(d,w)-> {
                if(w==0) chooseFavorites(); else chooseReorder();
            }).setNegativeButton("رجوع",null));
    }
    private void chooseFavorites() {
        refreshApps(); ArrayList<String> working = new ArrayList<>(favorites);
        String[] names=new String[apps.size()]; boolean[] checked=new boolean[apps.size()];
        for(int i=0;i<apps.size();i++) { App a=apps.get(i); names[i]=a.name+(a.installed?"":" (غير مثبت)"); checked[i]=working.contains(a.pkg); }
        dialog(new AlertDialog.Builder(this).setTitle("اختر تطبيقاتك — حتى ٨ تطبيقات")
            .setMultiChoiceItems(names,checked,(d,index,value)-> {
                String pkg=apps.get(index).pkg;
                if(value && working.size()>=8) { ((AlertDialog)d).getListView().setItemChecked(index,false); toast("يمكن عرض ٨ تطبيقات في المفضلة"); }
                else if(value) { if(!working.contains(pkg)) working.add(pkg); } else working.remove(pkg);
            }).setPositiveButton("حفظ",(d,w)-> { favorites.clear(); favorites.addAll(working); saveFavorites(); })
            .setNegativeButton("إلغاء",null));
    }
    private void chooseReorder() {
        if(favorites.isEmpty()) { chooseFavorites(); return; }
        String[] names=new String[favorites.size()];
        for(int i=0;i<names.length;i++) names[i]=findApp(favorites.get(i)).name;
        dialog(new AlertDialog.Builder(this).setTitle("اختر تطبيقًا لتعديله")
            .setItems(names,(d,w)->showAppOptions(findApp(favorites.get(w)))).setNegativeButton("رجوع",null));
    }
    private void showAppOptions(App app) {
        dialog(new AlertDialog.Builder(this).setTitle(app.name)
            .setItems(new String[]{"تقديم في الترتيب","تأخير في الترتيب","إزالة من المفضلة","معلومات التطبيق"},(d,w)-> {
                int index=favorites.indexOf(app.pkg);
                if(w==0 && index>0) { Collections.swap(favorites,index,index-1); saveFavorites(); }
                else if(w==1 && index>=0 && index<favorites.size()-1) { Collections.swap(favorites,index,index+1); saveFavorites(); }
                else if(w==2) { favorites.remove(app.pkg); saveFavorites(); }
                else if(w==3 && app.installed) safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.pkg)));
                else if(w<2) toast("التطبيق في طرف القائمة");
            }).setNegativeButton("رجوع",null));
    }
    private void showAllApps() {
        refreshApps(); ScrollView scroll=new ScrollView(this); scroll.setPadding(dp(20),dp(10),dp(20),dp(10));
        GridLayout grid=new GridLayout(this); grid.setColumnCount(4); grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for(App a:apps) {
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=dp(120); lp.height=dp(120); lp.setMargins(dp(6),dp(6),dp(6),dp(6));
            grid.addView(appCard(a,false),lp);
        }
        scroll.addView(grid,new ScrollView.LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL));
        AlertDialog d=dialog(new AlertDialog.Builder(this).setTitle("كل التطبيقات").setView(scroll).setNegativeButton("رجوع",null));
        d.getWindow().setLayout(dp(700),Math.min(dp(430),getResources().getDisplayMetrics().heightPixels-dp(32)));
    }

    private void showInputs() {
        ArrayList<TvInputInfo> inputs=new ArrayList<>(); ArrayList<String> names=new ArrayList<>();
        try {
            TvInputManager manager=(TvInputManager)getSystemService(TV_INPUT_SERVICE);
            if(manager!=null) for(TvInputInfo input:manager.getTvInputList()) {
                if(!input.isPassthroughInput()) continue;
                inputs.add(input); CharSequence custom=input.loadCustomLabel(this);
                names.add((custom!=null?custom:input.loadLabel(this)).toString());
            }
        } catch(Exception ignored) {}
        names.add("فتح شاشة المداخل في التلفاز");
        dialog(new AlertDialog.Builder(this).setTitle("المداخل")
            .setItems(names.toArray(new String[0]),(d,index)-> {
                if(index<inputs.size()) {
                    Uri uri=TvContract.buildChannelUriForPassthroughInput(inputs.get(index).getId());
                    safeStart(new Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    Intent inputMenu=new Intent("android.settings.TV_INPUT_SETTINGS");
                    if(getPackageManager().resolveActivity(inputMenu,0)!=null) safeStart(inputMenu);
                    else safeStart(new Intent(Settings.ACTION_SETTINGS));
                }
            }).setNegativeButton("رجوع",null));
    }

    private int selectedPhoto() {
        int fixed=Math.max(0,Math.min(photoFiles.length-1,prefs.getInt("photo",0)));
        if(!prefs.getBoolean("random_photos",true)) return fixed;
        long now=SystemClock.elapsedRealtime();
        long interval=Math.max(30000L,prefs.getLong("photo_interval_ms",120000L));
        if(randomPhoto<0 || now-lastPhotoChange>=interval) {
            randomPhoto=photoBag.next(randomPhoto);lastPhotoChange=now;
        }
        return randomPhoto;
    }
    private void loadWallpaper() {
        int index=selectedPhoto(); placeText.setText(photoNames[index]+"  ·  سلطنة عُمان");
        schedulePhotoRotation();
        if(wallpaperBitmap!=null && decodedPhoto==index) return;
        if(loadingPhoto==index && pendingPhoto!=null && !pendingPhoto.isDone())return;
        final int request=++photoRequest;
        if(pendingPhoto!=null)pendingPhoto.cancel(true);
        if(photoWorker.isShutdown())return;
        loadingPhoto=index;
        pendingPhoto=photoWorker.submit(() -> {
            final Bitmap bitmap=PhotoCatalog.decode(this,photoFiles[index]);
            if(Thread.currentThread().isInterrupted()){if(bitmap!=null)bitmap.recycle();return;}
            final Bitmap blur=bitmap==null?null:PhotoCatalog.frost(bitmap);
            handler.post(() -> {
                if(isDestroyed() || request!=photoRequest || bitmap==null) {
                    if(bitmap!=null) bitmap.recycle();if(blur!=null) blur.recycle();return;
                }
                loadingPhoto=-1;wallpaperBitmap=bitmap;wallpaperBlur=blur;decodedPhoto=index;wallpaper.setPhoto(bitmap,true);
                wallpaper.setContentDescription("خلفية: "+photoNames[index]);
                invalidateGlass(root);
                for(AlertDialog dialog:new ArrayList<>(openDialogs)) if(dialog.isShowing() && dialog.getWindow()!=null) invalidateGlass(dialog.getWindow().getDecorView());
                android.util.Log.i("Ufuq","Home photo: "+photoFiles[index]);
            });
        });
    }
    private void schedulePhotoRotation() {
        handler.removeCallbacks(photoRotate);
        if(active && prefs.getBoolean("random_photos",true)) {
            long interval=Math.max(30000L,prefs.getLong("photo_interval_ms",120000L));
            long remaining=interval-(SystemClock.elapsedRealtime()-lastPhotoChange);
            handler.postDelayed(photoRotate,Math.max(250L,remaining));
        }
    }
    private void invalidateGlass(View view) {
        view.invalidate();
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) invalidateGlass(group.getChildAt(i));
        }
    }
    private void showWallpapers() {
        LinearLayout body=column();body.setPadding(dp(20),0,dp(20),dp(8));
        LinearLayout toolbar=row();
        TextView random=galleryAction("","gallery-random");
        random.setText(prefs.getBoolean("random_photos",true)?"عشوائي: مفعّل":"عشوائي: متوقف");
        TextView duration=galleryAction("المدة: "+photoIntervalName(),"gallery-duration");
        TextView screensaver=galleryAction("شاشة السكون","gallery-screensaver");
        for(TextView action:new TextView[]{random,duration,screensaver}) {
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMargins(dp(4),0,dp(4),0);toolbar.addView(action,lp);
        }
        body.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(40)));gap(body,8);
        body.addView(text("اختر الصورة لتثبيتها كخلفية",12,MUTED),new LinearLayout.LayoutParams(-1,dp(20)));gap(body,6);
        GridView grid=new GridView(this);grid.setId(View.generateViewId());grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        grid.setNumColumns(4);grid.setColumnWidth(dp(170));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(12));grid.setPadding(dp(4),dp(4),dp(4),dp(4));
        grid.setClipToPadding(false);grid.setDrawSelectorOnTop(true);grid.setSelector(rounded(Color.TRANSPARENT,ACCENT,16));
        grid.setContentDescription("معرض خلفيات عُمان، "+photoFiles.length+" صورة");
        PhotoAdapter adapter=new PhotoAdapter();grid.setAdapter(adapter);
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){animateSelection(view);}
            public void onNothingSelected(AdapterView<?> parent){}
        });
        body.addView(grid,new LinearLayout.LayoutParams(-1,dp(272)));
        AlertDialog gallery=dialog(new AlertDialog.Builder(this).setTitle("خلفيات عُمان · "+photoFiles.length+" صورة · "+uhdPhotoCount+" بدقة 4K")
            .setView(body).setNegativeButton("رجوع",null));
        gallery.getWindow().setLayout(Math.min(dp(800),getResources().getDisplayMetrics().widthPixels-dp(80)),
            Math.min(dp(480),getResources().getDisplayMetrics().heightPixels-dp(48)));
        grid.setOnItemClickListener((parent,view,index,id)-> {
            prefs.edit().putBoolean("random_photos",false).putInt("photo",index).apply();loadWallpaper();gallery.dismiss();
        });
        random.setOnClickListener(view-> {
            boolean wasRandom=prefs.getBoolean("random_photos",true);int current=selectedPhoto();
            prefs.edit().putBoolean("random_photos",!wasRandom).putInt("photo",current).apply();
            if(!wasRandom) {randomPhoto=photoBag.next(current);lastPhotoChange=SystemClock.elapsedRealtime();}
            random.setText(wasRandom?"عشوائي: متوقف":"عشوائي: مفعّل");adapter.notifyDataSetChanged();loadWallpaper();
        });
        duration.setOnClickListener(view->choosePhotoInterval(()->duration.setText("المدة: "+photoIntervalName())));
        screensaver.setOnClickListener(view->showScreensaverSettings());
        for(TextView action:new TextView[]{random,duration,screensaver}) action.setNextFocusDownId(grid.getId());
        grid.setNextFocusUpId(random.getId());
        int initial=prefs.getBoolean("random_photos",true)?0:Math.max(0,Math.min(photoFiles.length-1,prefs.getInt("photo",0)));
        grid.postDelayed(()-> {if(gallery.isShowing()){grid.requestFocusFromTouch();grid.setSelection(initial);}},180);
    }
    private TextView galleryAction(String label,String key) {
        TextView action=text(label,13,WHITE);action.setGravity(Gravity.CENTER);action.setSingleLine(true);
        action.setEllipsize(TextUtils.TruncateAt.END);action.setPadding(dp(8),0,dp(8),0);focusable(action,key,false,12);return action;
    }
    private String photoIntervalName() {
        long value=prefs.getLong("photo_interval_ms",120000L);
        return value==30000L?"٣٠ ثانية":value==60000L?"دقيقة":value==300000L?"٥ دقائق":value==900000L?"١٥ دقيقة":"دقيقتان";
    }
    private class PhotoAdapter extends BaseAdapter {
        public int getCount(){return photoFiles.length;}
        public Object getItem(int position){return photoFiles[position];}
        public long getItemId(int position){return position;}
        public boolean hasStableIds(){return true;}
        public View getView(int position,View recycled,ViewGroup parent) {
            LinearLayout tile;ImageView preview;TextView label,selected;
            if(recycled==null) {
                tile=column();tile.setPadding(dp(6),dp(6),dp(6),dp(6));
                tile.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                tile.setBackground(new FrostedGlassDrawable(tile,root,()->wallpaperBlur,dp(16)));
                FrameLayout frame=new FrameLayout(MainActivity.this);frame.setBackground(rounded(0xFF192C32,0,10));frame.setClipToOutline(true);
                preview=new ImageView(MainActivity.this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
                selected=text("✓",12,0xFF17322D);selected.setGravity(Gravity.CENTER);selected.setShadowLayer(0,0,0,0);
                selected.setBackground(rounded(ACCENT,0,10));FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.TOP|Gravity.RIGHT);
                badge.setMargins(dp(5),dp(5),dp(5),dp(5));frame.addView(selected,badge);
                tile.addView(frame,new LinearLayout.LayoutParams(-1,dp(90)));gap(tile,4);
                label=text("",13,WHITE);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);
                tile.addView(label,new LinearLayout.LayoutParams(-1,dp(20)));
                tile.setTag(new View[]{preview,label,selected});tile.setLayoutParams(new AbsListView.LayoutParams(-1,dp(126)));
            } else {
                tile=(LinearLayout)recycled;View[] views=(View[])tile.getTag();preview=(ImageView)views[0];label=(TextView)views[1];selected=(TextView)views[2];
            }
            PhotoCatalog.Photo photo=photoCatalog.get(position);
            label.setText(photoNames[position]+(photo.isUhd()?"":" · أصلية"));
            tile.setContentDescription(photoNames[position]+" — "+photo.resolution());
            boolean fixed=!prefs.getBoolean("random_photos",true) && prefs.getInt("photo",0)==position;
            selected.setVisibility(fixed?View.VISIBLE:View.GONE);bindThumbnail(preview,photoFiles[position]);return tile;
        }
    }
    private void bindThumbnail(ImageView preview,String asset) {
        preview.setTag(asset);Bitmap cached=thumbnails.get(asset);
        if(cached!=null) {preview.setImageBitmap(cached);return;}
        preview.setImageDrawable(null);
        if(thumbnailWorker.isShutdown()) return;
        thumbnailWorker.execute(()-> {
            final Bitmap bitmap=PhotoCatalog.thumbnail(this,asset);
            handler.post(()-> {
                if(isDestroyed()) {if(bitmap!=null) bitmap.recycle();return;}
                if(bitmap!=null) {thumbnails.put(asset,bitmap);if(asset.equals(preview.getTag())) preview.setImageBitmap(bitmap);}
            });
        });
    }
    private void choosePhotoInterval(Runnable changed) {
        long[] intervals={30000L,60000L,120000L,300000L,900000L};
        String[] labels={"٣٠ ثانية","دقيقة","دقيقتان","٥ دقائق","١٥ دقيقة"};
        long interval=prefs.getLong("photo_interval_ms",120000L);int selected=2;
        for(int i=0;i<intervals.length;i++) if(interval==intervals[i]) selected=i;
        dialog(new AlertDialog.Builder(this).setTitle("مدة الصورة · الخلفية وشاشة السكون")
            .setSingleChoiceItems(labels,selected,(d,index)-> {
                prefs.edit().putLong("photo_interval_ms",intervals[index]).apply();
                lastPhotoChange=SystemClock.elapsedRealtime();schedulePhotoRotation();changed.run();d.dismiss();
            }).setNegativeButton("رجوع",null));
    }
    private void showScreensaverSettings() {
        dialog(new AlertDialog.Builder(this).setTitle("شاشة السكون · صور عُمان")
            .setMessage("أُفق يبقي الشاشة مضاءة أثناء عرض الواجهة، ولا يدخل السكون التلقائي. لإظهار الصور فقط، يمكنك تشغيل «أُفق — صور عُمان» يدويًا من إعداد شاشة السكون.\n\nتستخدم شاشة الصور المجموعة نفسها بترتيب عشوائي، حتى دون إنترنت. أي ضغطة بالريموت تعيدك للاستخدام.")
            .setPositiveButton("فتح إعداد شاشة السكون",(d,w)-> {
                Intent settings=new Intent(Settings.ACTION_DREAM_SETTINGS);
                if(getPackageManager().resolveActivity(settings,0)!=null) safeStart(settings);
                else {safeStart(new Intent(Settings.ACTION_SETTINGS));toast("تفضيلات الجهاز ← شاشة السكون ← أُفق — صور عُمان");}
            })
            .setNegativeButton("رجوع",null));
    }

    private void updateClockAndWeather() {
        if(clock==null) return;
        java.util.Date now=new java.util.Date();
        clock.setText(new SimpleDateFormat("HH:mm",Locale.US).format(now));
        date.setText(new SimpleDateFormat("EEEE، d MMMM",new Locale("ar")).format(now));
        cityText.setText(prefs.getString("city","عبري"));
        long timestamp=prefs.getLong("weather_time",0);
        if(timestamp>0) {
            temperature.setText(prefs.getInt("weather_temp",0)+"°م");
            int code=prefs.getInt("weather_code",0); weatherText.setText(WeatherRules.description(code,prefs.getBoolean("is_day",true)));
            weatherGlyph.kind=WeatherRules.icon(code,prefs.getBoolean("is_day",true)); weatherGlyph.invalidate();
            String time=new SimpleDateFormat("HH:mm",Locale.US).format(new java.util.Date(timestamp));
            boolean stale=System.currentTimeMillis()-timestamp>2*60*60*1000L;
            updateText.setText((stale?"قراءة قديمة · ":prefs.getBoolean("weather_error",false)?"تعذّر الاتصال · ":"تحديث ")+time+(weatherBusy?" · جارٍ التحديث":""));
        } else {
            temperature.setText("—°م"); weatherText.setText(weatherBusy?"جارٍ جلب الطقس":"الطقس غير متاح");
            updateText.setText(weatherBusy?"":"اضغط لإعادة المحاولة");
        }
        if(placeText!=null) placeText.setText(photoNames[selectedPhoto()]+"  ·  سلطنة عُمان");
        if(active && wallpaperBitmap!=null && prefs.getInt("decoded_photo",-1)!=selectedPhoto()) loadWallpaper();
    }
    private void maybeWeather(boolean force) {
        if(weatherBusy || worker.isShutdown()) return;
        long age=System.currentTimeMillis()-prefs.getLong("weather_fetch_time",0);
        if(!force && (age<30*60*1000L || System.currentTimeMillis()-lastAttempt<5*60*1000L)) return;
        weatherBusy=true; lastAttempt=System.currentTimeMillis(); updateClockAndWeather();
        final int request=++weatherRequest;
        final String lat=prefs.getString("latitude","23.22573"), lon=prefs.getString("longitude","56.51572");
        worker.execute(() -> {
            try {
                String url="https://api.open-meteo.com/v1/forecast?latitude="+URLEncoder.encode(lat,"UTF-8")+
                    "&longitude="+URLEncoder.encode(lon,"UTF-8")+"&current=temperature_2m,weather_code,is_day&timezone=auto&timeformat=unixtime";
                JSONObject current=fetchJson(url).getJSONObject("current");
                final int temp=(int)Math.round(current.getDouble("temperature_2m"));
                final int code=current.getInt("weather_code"); final boolean day=current.optInt("is_day",1)==1;
                // API observation time is distinct from fetch time, so a cached server reading stays honest.
                final long observed=current.getLong("time")*1000L;
                handler.post(() -> {
                    if(isDestroyed() || request!=weatherRequest) return;
                    prefs.edit().putInt("weather_temp",temp).putInt("weather_code",code).putBoolean("is_day",day)
                        .putLong("weather_time",observed).putLong("weather_fetch_time",System.currentTimeMillis()).putBoolean("weather_error",false).apply();
                    weatherBusy=false; updateClockAndWeather();
                });
            } catch(Exception e) {
                android.util.Log.w("Ufuq","Weather request failed",e);
                handler.post(() -> { if(request!=weatherRequest || isDestroyed()) return; weatherBusy=false; prefs.edit().putBoolean("weather_error",true).apply(); updateClockAndWeather(); if(force) toast("تعذّر تحديث الطقس. تحقق من اتصال الإنترنت."); });
            }
        });
    }
    private JSONObject fetchJson(String url) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent","UfuqTV/1.0 (personal TV launcher)");
        try {
            if(connection.getResponseCode()!=200) throw new IOException("HTTP "+connection.getResponseCode());
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"))) {
                StringBuilder s=new StringBuilder(); String line;
                while((line=reader.readLine())!=null) { s.append(line); if(s.length()>500000) throw new IOException("Response too large"); }
                return new JSONObject(s.toString());
            }
        } finally { connection.disconnect(); }
    }
    private void showWeatherMenu() {
        dialog(new AlertDialog.Builder(this).setTitle(prefs.getString("city","عبري")+" · الطقس")
            .setMessage(prefs.getString("location_note","")+"\nالحرارة بالدرجة المئوية. البيانات من Open-Meteo.")
            .setPositiveButton("تحديث الآن",(d,w)->maybeWeather(true))
            .setNeutralButton("تغيير المدينة",(d,w)->chooseCity()).setNegativeButton("رجوع",null));
    }
    private void chooseCity() {
        EditText input=new EditText(this); input.setSingleLine(true); input.setText(prefs.getString("city","عبري"));
        input.setSelectAllOnFocus(true); input.setTextSize(19); input.setHint("اسم المدينة بالعربية أو الإنجليزية");
        input.setTextColor(WHITE); input.setPadding(dp(20),dp(16),dp(20),dp(16));
        AlertDialog d=dialog(new AlertDialog.Builder(this).setTitle("مدينة الطقس").setView(input)
            .setPositiveButton("بحث",(x,w)-> {
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
                searchCity(input.getText().toString().trim());
            }).setNegativeButton("إلغاء",null));
    }
    private void searchCity(String name) {
        if(name.length()<2) { toast("اكتب اسم مدينة"); return; }
        if(name.equals("عبري") || name.equalsIgnoreCase("Ibri") || name.equalsIgnoreCase("Ibrī")) {
            dialog(new AlertDialog.Builder(this).setTitle("اختر الموقع")
                .setItems(new String[]{"عبري، الظاهرة، عُمان — قراءة تقريبية للهجر"},(d,index)-> {
                    ++weatherRequest; weatherBusy=false; lastAttempt=0;
                    prefs.edit().putString("latitude","23.22573").putString("longitude","56.51572")
                        .putString("city","عبري").putString("location_note","قراءة تقريبية للهجر")
                        .remove("weather_time").remove("weather_fetch_time").putBoolean("weather_error",false).apply();
                    updateClockAndWeather(); maybeWeather(true);
                }).setNegativeButton("رجوع",null));
            return;
        }
        toast("جارٍ البحث عن المدينة");
        worker.execute(() -> {
            try {
                JSONObject response=fetchJson("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(name,"UTF-8")+"&count=8&language=ar&format=json");
                JSONArray hits=response.optJSONArray("results");
                if(hits==null || hits.length()==0) { handler.post(()->toast("لم تظهر نتائج. جرّب اسم المدينة بالإنجليزية.")); return; }
                String[] names=new String[hits.length()];
                for(int i=0;i<names.length;i++) { JSONObject h=hits.getJSONObject(i); names[i]=h.getString("name")+"، "+h.optString("admin1","")+"، "+h.optString("country",""); }
                handler.post(() -> {
                    if(isDestroyed() || !active) return;
                    dialog(new AlertDialog.Builder(this).setTitle("اختر الموقع")
                        .setItems(names,(d,index)-> {
                            try {
                                JSONObject h=hits.getJSONObject(index); ++weatherRequest; weatherBusy=false; lastAttempt=0;
                                prefs.edit().putString("latitude",h.getString("latitude")).putString("longitude",h.getString("longitude"))
                                    .putString("city",h.getString("name")).putString("location_note",h.optString("admin1","")+"، "+h.optString("country",""))
                                    .remove("weather_time").remove("weather_fetch_time").putBoolean("weather_error",false).apply();
                                updateClockAndWeather(); maybeWeather(true);
                            } catch(JSONException e) { toast("تعذّر اختيار الموقع"); }
                        }).setNegativeButton("رجوع",null));
                });
            } catch(Exception e) { handler.post(()->toast("تعذّر البحث. تحقق من اتصال الإنترنت.")); }
        });
    }

    private void showSettings() {
        dialog(new AlertDialog.Builder(this).setTitle("الإعدادات")
            .setItems(new String[]{"تخصيص التطبيقات","الخلفيات","شاشة السكون","الطقس والمدينة","تعيين أُفق كواجهة رئيسية","فتح الواجهة الأصلية","إعدادات التلفاز","القرد المشاغب","حول أُفق ومصادر الصور"},(d,index)-> {
                if(index==0) showFavoriteSettings(); else if(index==1) showWallpapers();
                else if(index==2) showScreensaverSettings();else if(index==3) showWeatherMenu(); else if(index==4) chooseHome();
                else if(index==5) originalHome(); else if(index==6) safeStart(new Intent(Settings.ACTION_SETTINGS));
                else if(index==7) showMonkeySettings();
                else showAbout();
            }).setNegativeButton("رجوع",null));
    }
    private void showMonkeySettings(){
        boolean enabled=prefs.getBoolean(MonkeyService.ENABLED,false);
        dialog(new AlertDialog.Builder(this).setTitle("القرد المشاغب · "+(enabled?"مفعّل":"متوقف"))
            .setItems(new String[]{enabled?"إيقاف الظهور":"تشغيل الظهور","نسبة الظهور وعدد الفرص","تجربة جميع الحركات · ١٨ ثانية","إيقاف القرد الآن"},(d,index)->{
                if(index==3||(index==0&&enabled)){MonkeyService.stop(this);toast("تم إيقاف القرد");}
                else if(index==1)showMonkeySchedule();
                else requestMonkey(index==0?MonkeyService.RANDOM:MonkeyService.DEMO);
            }).setNegativeButton("رجوع",null));
    }
    private void showMonkeySchedule(){
        LinearLayout panel=column();panel.setPadding(dp(22),dp(8),dp(22),dp(8));
        TextView chanceLabel=text("",16,WHITE);panel.addView(chanceLabel);
        SeekBar chance=new SeekBar(this);chance.setId(View.generateViewId());chance.setMax(100);
        chance.setProgress(MonkeySchedule.chance(prefs.getInt(MonkeyService.CHANCE,MonkeySchedule.DEFAULT_CHANCE_PERCENT)));
        panel.addView(chance,new LinearLayout.LayoutParams(-1,dp(42)));gap(panel,12);
        TextView attemptsLabel=text("",16,WHITE);panel.addView(attemptsLabel);
        SeekBar attempts=new SeekBar(this);attempts.setId(View.generateViewId());attempts.setMax(MonkeySchedule.MAX_ATTEMPTS-1);
        final int oldAttempts=MonkeySchedule.attempts(prefs.getInt(MonkeyService.ATTEMPTS,MonkeySchedule.DEFAULT_ATTEMPTS));
        attempts.setProgress(oldAttempts-1);panel.addView(attempts,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView hint=text("تُطبَّق النسبة على كل فرصة. استخدم يمين ويسار للتعديل.",12,MUTED);panel.addView(hint);
        Runnable labels=()->{
            chanceLabel.setText("نسبة الظهور: "+chance.getProgress()+"٪");
            attemptsLabel.setText("فرص الظهور في الساعة: "+(attempts.getProgress()+1));
            chance.setContentDescription("نسبة الظهور: "+chance.getProgress()+"٪");
            attempts.setContentDescription("فرص الظهور في الساعة: "+(attempts.getProgress()+1));
        };
        for(SeekBar bar:new SeekBar[]{chance,attempts}){
            bar.setFocusable(true);bar.setFocusableInTouchMode(true);bar.setKeyProgressIncrement(1);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(WHITE));
            bar.setThumbTintList(android.content.res.ColorStateList.valueOf(WHITE));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar view,int value,boolean fromUser){labels.run();}
                public void onStartTrackingTouch(SeekBar view){}
                public void onStopTrackingTouch(SeekBar view){}
            });
        }
        labels.run();
        AlertDialog settings=dialog(new AlertDialog.Builder(this).setTitle("نسبة الظهور وعدد الفرص").setView(panel)
            .setPositiveButton("حفظ",(d,w)->{
                int count=attempts.getProgress()+1;
                SharedPreferences.Editor edit=prefs.edit().putInt(MonkeyService.CHANCE,chance.getProgress()).putInt(MonkeyService.ATTEMPTS,count);
                if(count!=oldAttempts)edit.remove(MonkeyService.NEXT_CHECK);edit.apply();
                if(prefs.getBoolean(MonkeyService.ENABLED,false)&&Settings.canDrawOverlays(this))MonkeyService.start(this,MonkeyService.RANDOM);
                toast("تم حفظ نسبة الظهور وعدد الفرص");
            }).setNegativeButton("إلغاء",null));
        Button save=settings.getButton(AlertDialog.BUTTON_POSITIVE),cancel=settings.getButton(AlertDialog.BUTTON_NEGATIVE);
        chance.setNextFocusUpId(chance.getId());chance.setNextFocusDownId(attempts.getId());
        attempts.setNextFocusUpId(chance.getId());attempts.setNextFocusDownId(save.getId());
        for(Button button:new Button[]{save,cancel}){button.setNextFocusUpId(attempts.getId());button.setNextFocusDownId(button.getId());}
        save.setNextFocusLeftId(cancel.getId());save.setNextFocusRightId(cancel.getId());
        cancel.setNextFocusLeftId(save.getId());cancel.setNextFocusRightId(save.getId());chance.requestFocus();
    }
    private void requestMonkey(String action){
        if(Settings.canDrawOverlays(this)){MonkeyService.start(this,action);return;}
        pendingMonkeyAction=action;
        try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),929);}
        catch(ActivityNotFoundException error){pendingMonkeyAction=null;toast("اسمح لأُفق بالظهور فوق التطبيقات من إعدادات التلفاز");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==929&&pendingMonkeyAction!=null){String action=pendingMonkeyAction;pendingMonkeyAction=null;
            if(Settings.canDrawOverlays(this))MonkeyService.start(this,action);
            else toast("يحتاج القرد إلى السماح بالظهور فوق التطبيقات");
        }
    }
    private void chooseHome() {
        if(Build.VERSION.SDK_INT>=29) {
            RoleManager roles=(RoleManager)getSystemService(ROLE_SERVICE);
            if(roles!=null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if(roles.isRoleHeld(RoleManager.ROLE_HOME)) { toast("أُفق هو الواجهة الرئيسية حاليًا"); return; }
                safeStart(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)); return;
            }
        }
        safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private void originalHome() {
        PackageManager pm=getPackageManager();
        ArrayList<ResolveInfo> others=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();ArrayList<Boolean> disabled=new ArrayList<>();
        for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DISABLED_COMPONENTS)) {
            String pkg=r.activityInfo.packageName;
            // Factory diagnostics and Settings' boot fallback are not user home screens.
            if(pkg.equals(getPackageName()) || pkg.equals("android") || pkg.equals("com.duokan.factorytest") || r.activityInfo.name.endsWith("FallbackHome") || !r.activityInfo.exported) continue;
            int appState=pm.getApplicationEnabledSetting(pkg);
            int componentState=pm.getComponentEnabledSetting(new ComponentName(pkg,r.activityInfo.name));
            // Setup/recovery HOME activities disabled individually cannot be restored
            // by the package's Enable button, and must not appear as launchers.
            if(!r.activityInfo.enabled || componentState>PackageManager.COMPONENT_ENABLED_STATE_ENABLED) continue;
            boolean off=!r.activityInfo.applicationInfo.enabled || appState>PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            others.add(r);disabled.add(off);labels.add(r.loadLabel(pm).toString()+(off?" — متوقفة":""));
        }
        if(others.isEmpty()) { toast("لا توجد واجهة أخرى متاحة"); return; }
        dialog(new AlertDialog.Builder(this).setTitle("فتح الواجهة الأصلية")
            .setItems(labels.toArray(new String[0]),(d,index)-> {
                ResolveInfo r=others.get(index);
                if(disabled.get(index)) {
                    dialog(new AlertDialog.Builder(this).setTitle("استعادة الواجهة الأصلية")
                        .setMessage("واجهة النظام متوقفة لهذا المستخدم. افتح معلومات التطبيق واضغط «تفعيل» أو «تمكين»، ثم اضغط زر الهوم.\n\nستعود واجهة شاومي إذا أعاد النظام منحها الأولوية.")
                        .setPositiveButton("فتح معلومات التطبيق",(x,w)->safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+r.activityInfo.packageName))))
                        .setNegativeButton("رجوع",null));return;
                }
                safeStart(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }).setNegativeButton("رجوع",null));
    }
    private void showAbout() {
        String credits="أُفق "+versionName()+"\nالمطور: M7MD929\nواجهة عربية للتلفاز، بدون إعلانات أو تتبّع.\n\n"+
            photoFiles.length+" صورة حقيقية من عُمان، محفوظة على الجهاز للخلفية وشاشة السكون. منها "+uhdPhotoCount+" صورة بحجم 3840 × 2160، وبقية الصور بدقتها الأصلية كما يوضح المعرض. دقة عرض الواجهة نفسها تعتمد على التلفاز.\n\n"+
            "مصادر الصور وأسماء المصورين وحقوق كل صورة محفوظة في ملف المصادر. صور الهجر موثّقة من صفحات تتناول البلدة وواديها بولاية عبري.\n\n"+
            "الطقس: Open-Meteo، بيانات CC BY 4.0. الموقع المبدئي عبري، قراءة تقريبية للهجر. يتم إرسال إحداثيات المدينة المختارة لخدمة الطقس فقط.\n\n"+
            "روابط الصور والرخص محفوظة في ملف المصادر داخل التطبيق.";
        TextView t=text(credits,15,WHITE);t.setPadding(dp(24),dp(12),dp(24),dp(12));
        showReaderDialog("حول أُفق",t,true);
    }
    private String versionName() {
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(PackageManager.NameNotFoundException error){return "1.9.5";}
    }
    private void showCredits() {
        try(InputStream in=getAssets().open("credits.txt")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
            while((n=in.read(b))!=-1) out.write(b,0,n);
            TextView t=text(out.toString("UTF-8"),13,WHITE);
            t.setPadding(dp(20),dp(10),dp(20),dp(10));
            android.text.util.Linkify.addLinks(t,android.text.util.Linkify.WEB_URLS);t.setLinkTextColor(ACCENT);
            showReaderDialog("المصادر والرخص",t,false);
        } catch(IOException e) { toast("تعذّر فتح المصادر"); }
    }
    private void showReaderDialog(String title,TextView body,boolean sources){
        ScrollView scroll=new ScrollView(this);scroll.setId(View.generateViewId());
        scroll.setFocusable(true);scroll.setFocusableInTouchMode(true);scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        body.setTextIsSelectable(false);body.setFocusable(false);scroll.addView(body);
        FrameLayout viewport=new FrameLayout(this);
        viewport.addView(scroll,new FrameLayout.LayoutParams(-1,Math.min(dp(300),getResources().getDisplayMetrics().heightPixels-dp(220))));
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(title).setView(viewport).setNegativeButton("رجوع",null);
        if(sources)builder.setNeutralButton("المصادر والرخص",(d,w)->showCredits());
        AlertDialog reader=dialog(builder);Button back=reader.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button first=sources?reader.getButton(AlertDialog.BUTTON_NEUTRAL):back;
        for(Button button:new Button[]{first,back}){
            button.setFocusable(true);button.setFocusableInTouchMode(true);button.setNextFocusUpId(scroll.getId());
            button.setNextFocusDownId(button.getId());button.setNextFocusLeftId(button==first?back.getId():first.getId());
            button.setNextFocusRightId(button==first?back.getId():first.getId());
        }
        scroll.setNextFocusDownId(first.getId());
        scroll.setOnKeyListener((view,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||
                (key==KeyEvent.KEYCODE_DPAD_DOWN&&!scroll.canScrollVertically(1))){first.requestFocus();return true;}
            return false;
        });
        first.requestFocus();
    }

    @Override public void onBackPressed() {
        if(firstCard!=null) firstCard.requestFocusFromTouch();
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_MENU && event.getRepeatCount()==0) { showSettings(); return true; }
        return super.onKeyDown(key,event);
    }

    private class Shade extends View {
        final Paint paint=new Paint();
        Shade(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c) {
            int w=getWidth(),h=getHeight();
            paint.setShader(new LinearGradient(0,0,0,h,new int[]{0x88040C13,0x18091117,0x880A141B,0xCA09141B},new float[]{0,.34f,.66f,1},Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,paint); paint.setShader(null);
        }
    }

    private class Glyph extends View {
        String kind; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(Context c,String k){super(c);kind=k;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
        @Override protected void onDraw(Canvas original) {
            Canvas c=original;c.save();float unit=Math.min(getWidth(),getHeight())/48f;
            c.translate((getWidth()-48*unit)/2f,(getHeight()-48*unit)/2f);c.scale(unit,unit);
            p.setColor(WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.3f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("youtube")) {
                p.setStyle(Paint.Style.FILL);p.setColor(0xFFFF303D);c.drawRoundRect(1,8,47,40,9,9,p);
                p.setColor(Color.WHITE);Path path=new Path();path.moveTo(20,16);path.lineTo(20,32);path.lineTo(33,24);path.close();c.drawPath(path,p);
            } else if(kind.equals("plus")) { line(c,10,24,38,24); line(c,24,10,24,38); }
            else if(kind.equals("apps")) { for(int x:new int[]{8,28}) for(int y:new int[]{8,28}) c.drawRoundRect(x,y,x+12,y+12,3,3,p); }
            else if(kind.equals("input")) { c.drawRoundRect(5,9,43,35,4,4,p); line(c,18,42,30,42); line(c,24,36,24,42); line(c,15,22,32,22); line(c,26,16,32,22);line(c,26,28,32,22); }
            else if(kind.equals("photo")) { c.drawRoundRect(5,7,43,41,4,4,p);c.drawCircle(32,17,4,p);Path x=new Path();x.moveTo(6,34);x.lineTo(18,22);x.lineTo(28,33);x.lineTo(35,27);x.lineTo(42,35);c.drawPath(x,p); }
            else if(kind.equals("tune")) {line(c,7,12,41,12);line(c,7,24,41,24);line(c,7,36,41,36);p.setStyle(Paint.Style.FILL);c.drawCircle(17,12,4,p);c.drawCircle(31,24,4,p);c.drawCircle(21,36,4,p);}
            else if(kind.equals("gear")) {Path cog=new Path();for(int i=0;i<32;i++){double a=Math.toRadians(i*11.25-5.625);float r=(i%4<2)?18:14;float x=24+(float)Math.cos(a)*r,y=24+(float)Math.sin(a)*r;if(i==0)cog.moveTo(x,y);else cog.lineTo(x,y);}cog.close();c.drawPath(cog,p);c.drawCircle(24,24,6,p);}
            else if(kind.equals("moon")) { Path m=new Path();m.addCircle(23,24,14,Path.Direction.CW);Path cut=new Path();cut.addCircle(31,17,13,Path.Direction.CW);m.op(cut,Path.Op.DIFFERENCE);p.setStyle(Paint.Style.FILL);c.drawPath(m,p); }
            else if(kind.equals("cloud") || kind.equals("rain") || kind.equals("storm") || kind.equals("snow") || kind.equals("fog")) {
                Path cloud=new Path();cloud.moveTo(10,32);cloud.cubicTo(0,30,2,17,13,18);cloud.cubicTo(16,3,36,8,35,20);cloud.cubicTo(49,18,49,34,37,34);cloud.lineTo(12,34);c.drawPath(cloud,p);
                if(kind.equals("rain")) {line(c,15,39,12,45);line(c,26,39,23,45);line(c,37,39,34,45);}
                if(kind.equals("storm")) {line(c,26,35,20,42);line(c,20,42,28,42);line(c,28,42,23,47);}
                if(kind.equals("snow")) { c.drawCircle(16,42,1.5f,p);c.drawCircle(29,42,1.5f,p); }
                if(kind.equals("fog")) {line(c,8,39,40,39);line(c,12,44,35,44);}
            } else {
                p.setColor(0xFFF5D28C);c.drawCircle(24,24,9,p);
                for(int a=0;a<360;a+=45){double r=Math.toRadians(a);line(c,24+(float)Math.cos(r)*15,24+(float)Math.sin(r)*15,24+(float)Math.cos(r)*20,24+(float)Math.sin(r)*20);}
            }
            c.restore();
        }
    }
}
