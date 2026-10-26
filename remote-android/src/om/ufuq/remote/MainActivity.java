package om.ufuq.remote;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Portrait companion remote: offline Oman gallery, local pairing, no ads. */
public class MainActivity extends Activity {
    private static final int WHITE=Color.rgb(248,251,251),MUTED=Color.rgb(211,222,225);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newSingleThreadExecutor();
    private final ArrayList<View> controls=new ArrayList<>();private final ArrayList<AlertDialog> dialogs=new ArrayList<>();
    private android.content.SharedPreferences prefs;private RemoteSession remote;private TvDiscovery discovery;
    private FrameLayout root;private PhotoFrame photo;private MonkeyView monkey;private TextView connection,deviceName,place;
    private Bitmap blurred;private PhotoCatalog.ShuffleBag bag;private java.util.List<PhotoCatalog.Photo> photos;
    private int photoIndex=-1,photoRequest;private boolean active,connecting,photoBusy;private String host="",name="التلفاز";private int port=6466;
    private AlertDialog codeDialog;private int repeatKey=-1;private long nextMonkeyCheck;private final Random random=new Random();
    private final Runnable repeat=new Runnable(){public void run(){if(repeatKey!=-1&&active){send(repeatKey,false);ui.postDelayed(this,100);}}};
    private final Runnable rotate=new Runnable(){public void run(){if(active){loadPhoto(true);ui.postDelayed(this,120000);}}};
    private final Runnable prank=new Runnable(){public void run(){if(!active)return;if(System.currentTimeMillis()>=nextMonkeyCheck){nextMonkeyCheck=System.currentTimeMillis()+3600000;if(prefs.getBoolean("monkey",true)&&random.nextInt(100)<prefs.getInt("monkey_chance",2))monkey.play();}ui.postDelayed(this,10000);}};
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);prefs=getSharedPreferences("ufuq-remote",MODE_PRIVATE);
        host=prefs.getString("host","");port=prefs.getInt("port",6466);name=prefs.getString("name","التلفاز");
        photos=PhotoCatalog.load(this);bag=new PhotoCatalog.ShuffleBag(photos.size());discovery=new TvDiscovery(this);
        build();workers.execute(()->{try{ClientIdentity identity=new ClientIdentity();ui.post(()->{if(isFinishing()||isDestroyed())return;remote=new RemoteSession(identity,new Events());if(active&&!host.isEmpty())connectSaved();});}
            catch(Exception error){ui.post(()->connection.setText("تعذّر تجهيز الاقتران؛ أعد فتح التطبيق"));}});
    }
    private void build(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(9,20,24));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);setContentView(root);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        photo=new PhotoFrame(this);root.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xD80A151C,0x8A091418,0xF2091418}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=column();content.setPadding(dp(24),dp(18),dp(24),dp(12));content.setGravity(Gravity.CENTER_HORIZONTAL);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        scroll.setOnApplyWindowInsetsListener((view,insets)->{scroll.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout top=row();TextView brand=text("أُفق ريموت",29,WHITE);brand.setTypeface(null,1);top.addView(brand,new LinearLayout.LayoutParams(0,dp(45),1));
        TextView power=button("power","تشغيل أو إطفاء التلفاز",48,48);key(power,26,false);top.addView(power);space(top,8);TextView settings=button("settings","الإعدادات",48,48);settings.setOnClickListener(v->settings());top.addView(settings);content.addView(top,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=text("تلفازك، بلمسة.",13,MUTED);sub.setGravity(Gravity.RIGHT);content.addView(sub,new LinearLayout.LayoutParams(-1,dp(26)));
        gap(content,10);LinearLayout target=row();target.setPadding(dp(16),dp(8),dp(12),dp(8));target.setBackground(new Glass(target,22));
        LinearLayout targetText=column();deviceName=text(host.isEmpty()?"اختر التلفاز":name,16,WHITE);deviceName.setTypeface(null,1);targetText.addView(deviceName);
        connection=text("غير متصل · اضغط للاتصال",12,MUTED);targetText.addView(connection);target.addView(targetText,new LinearLayout.LayoutParams(0,-2,1));
        TextView change=text("⌁",25,WHITE);change.setGravity(Gravity.CENTER);target.addView(change,new LinearLayout.LayoutParams(dp(32),dp(38)));target.setContentDescription("اختيار التلفاز والاتصال");target.setClickable(true);target.setOnClickListener(v->devices());content.addView(target,new LinearLayout.LayoutParams(-1,-2));
        gap(content,14);int padSize=Math.min(250,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-52);
        FrameLayout pad=new FrameLayout(this);pad.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);pad.setBackground(new Glass(pad,padSize/2));content.addView(pad,new LinearLayout.LayoutParams(dp(padSize),dp(padSize)));
        int tile=64,offset=(padSize-tile)/2;
        padKey(pad,"up","أعلى",19,offset,4,tile);padKey(pad,"down","أسفل",20,offset,padSize-tile-4,tile);
        padKey(pad,"left","يسار",21,4,offset,tile);padKey(pad,"right","يمين",22,padSize-tile-4,offset,tile);
        TextView ok=button("ok","موافق",84,84);ok.setText("OK");ok.setTextSize(23);ok.setTypeface(null,1);ok.setBackground(new Glass(ok,42,0x3AFFFFFF));
        FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(dp(84),dp(84),Gravity.CENTER);pad.addView(ok,center);key(ok,23,false);
        gap(content,12);LinearLayout navigation=row();navigation.addView(remoteButton("back","رجوع",4),weighted());space(navigation,10);navigation.addView(remoteButton("home","الهوم",3),weighted());space(navigation,10);navigation.addView(remoteButton("menu","القائمة",82),weighted());content.addView(navigation,new LinearLayout.LayoutParams(-1,dp(48)));
        gap(content,10);LinearLayout volume=row();volume.addView(remoteButton("volumeup","رفع الصوت",24),weighted());space(volume,10);volume.addView(remoteButton("mute","كتم الصوت",164),weighted());space(volume,10);volume.addView(remoteButton("volumedown","خفض الصوت",25),weighted());content.addView(volume,new LinearLayout.LayoutParams(-1,dp(48)));
        gap(content,10);LinearLayout extra=row();extra.addView(remoteButton("input","المداخل",178),weighted());space(extra,10);extra.addView(remoteButton("play","تشغيل أو إيقاف مؤقت",85),weighted());space(extra,10);
        TextView keyboard=button("keyboard","كتابة على التلفاز",0,48);keyboard.setOnClickListener(v->keyboard());controls.add(keyboard);extra.addView(keyboard,weighted());content.addView(extra,new LinearLayout.LayoutParams(-1,dp(48)));
        gap(content,10);LinearLayout shortcuts=row();TextView youtube=button("youtube","يوتيوب",0,44);youtube.setText("YouTube");youtube.setTextSize(14);youtube.setOnClickListener(v->{if(connected())remote.appLink("https://www.youtube.com/tv");});controls.add(youtube);shortcuts.addView(youtube,weighted());space(shortcuts,10);
        TextView ufuq=button("home","أُفق",0,44);ufuq.setText("أُفق  ⌂");ufuq.setTextSize(16);key(ufuq,3,false);shortcuts.addView(ufuq,weighted());content.addView(shortcuts,new LinearLayout.LayoutParams(-1,dp(44)));
        gap(content,10);place=text("سلطنة عُمان",10,MUTED);place.setGravity(Gravity.CENTER);content.addView(place,new LinearLayout.LayoutParams(-1,dp(18)));
        TextView signature=text("M7MD929 · \u2067أُفق ريموت\u2069 1.0.0",10,MUTED);signature.setTextDirection(View.TEXT_DIRECTION_LTR);signature.setGravity(Gravity.CENTER);content.addView(signature,new LinearLayout.LayoutParams(-1,dp(20)));
        monkey=new MonkeyView(this);root.addView(monkey,new FrameLayout.LayoutParams(-1,-1));setConnected(false);loadPhoto(false);
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void space(LinearLayout parent,int width){parent.addView(new View(this),new LinearLayout.LayoutParams(dp(width),1));}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,-1,1);}
    private TextView button(String icon,String label,int width,int height){
        TextView v=text("",20,WHITE);v.setGravity(Gravity.CENTER);v.setContentDescription(label);v.setBackground(new Glass(v,18));v.setFocusable(true);v.setClickable(true);
        v.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
        if(!icon.equals("ok"))v.setBackground(new ButtonBackground(v,icon));
        if(width>0)v.setLayoutParams(new LinearLayout.LayoutParams(dp(width),dp(height)));v.setMinHeight(dp(height));return v;
    }
    private TextView remoteButton(String icon,String label,int code){TextView v=button(icon,label,0,48);key(v,code,code==24||code==25);return v;}
    private void padKey(FrameLayout parent,String icon,String label,int code,int x,int y,int size){TextView v=button(icon,label,size,size);v.setBackground(new ButtonBackground(v,icon,true));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(size),dp(size));lp.leftMargin=dp(x);lp.topMargin=dp(y);parent.addView(v,lp);key(v,code,true);}
    private void key(TextView view,int code,boolean repeating){controls.add(view);view.setOnClickListener(v->send(code,true));
        if(repeating)view.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){if(!v.isEnabled())return false;send(code,true);repeatKey=code;v.setAlpha(.65f);ui.removeCallbacks(repeat);ui.postDelayed(repeat,420);return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){repeatKey=-1;ui.removeCallbacks(repeat);v.setAlpha(v.isEnabled()?1:.38f);return true;}return true;});
    }
    private boolean connected(){if(remote==null||!remote.isReady()){toast("اتصل بالتلفاز أولًا");return false;}return true;}
    private void send(int code,boolean haptic){if(!connected())return;remote.key(code,3);if(haptic)root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}
    private void setConnected(boolean value){for(View control:controls){control.setEnabled(value);control.setAlpha(value?1:.38f);}}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    private void loadPhoto(boolean animate){if(photoBusy)return;photoBusy=true;int request=++photoRequest;int fixed=prefs.getInt("photo",-1);int index=fixed>=0&&fixed<photos.size()?fixed:bag.next(photoIndex);photoIndex=index;
        workers.execute(()->{Bitmap bitmap=PhotoCatalog.decode(this,photos.get(index).asset);Bitmap frost=bitmap==null?null:PhotoCatalog.frost(bitmap);ui.post(()->{photoBusy=false;if(isDestroyed()||request!=photoRequest)return;if(bitmap!=null){photo.setPhoto(bitmap,animate);blurred=frost;place.setText(photos.get(index).name+" · سلطنة عُمان");invalidateGlass(root);}});});
    }
    private void invalidateGlass(View view){view.invalidate();if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)invalidateGlass(group.getChildAt(i));}}
    private AlertDialog dialog(String title,View body){AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(body).setNegativeButton("رجوع",null).create();return show(d);}
    private AlertDialog show(AlertDialog d){dialogs.add(d);d.setOnDismissListener(v->dialogs.remove(d));d.show();if(d.getWindow()!=null){View decor=d.getWindow().getDecorView();d.getWindow().setBackgroundDrawable(new Glass(decor,24,0x99253540));decor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);}return d;}
    private LinearLayout dialogBody(){LinearLayout b=column();b.setPadding(dp(22),dp(12),dp(22),dp(10));return b;}
    private TextView item(String label,Runnable action){TextView t=text(label,16,WHITE);t.setPadding(dp(10),dp(15),dp(10),dp(15));t.setMinHeight(dp(54));t.setBackground(new Glass(t,15));t.setFocusable(true);t.setClickable(true);t.setOnClickListener(v->action.run());return t;}
    private void devices(){LinearLayout b=dialogBody();TextView hint=text("الهاتف والتلفاز على شبكة واي فاي واحدة. اختر جهازًا أو أدخل عنوانه.",13,MUTED);b.addView(hint);gap(b,12);
        LinearLayout results=column();b.addView(results);TextView finding=text("جارٍ البحث عن التلفاز…",14,MUTED);results.addView(finding);gap(b,12);
        final AlertDialog[] picker={null};TextView manual=item("إدخال عنوان التلفاز يدويًا",()->{picker[0].dismiss();manual();});b.addView(manual);picker[0]=dialog("الاتصال بالتلفاز",b);
        picker[0].setOnDismissListener(v->{dialogs.remove(picker[0]);discovery.stop();});HashSet<String> seen=new HashSet<>();
        discovery.start(new TvDiscovery.Callback(){public void info(String value){finding.setText(value);}public void found(String label,String address,int remotePort){String id=address+":"+remotePort;if(!seen.add(id))return;finding.setText("الأجهزة المتاحة");TextView result=item(label+"\n"+address,()->{picker[0].dismiss();select(address,remotePort,label);});results.addView(result);}});
        if(!host.isEmpty()){TextView saved=item("آخر تلفاز: "+name,()->{picker[0].dismiss();select(host,port,name);});b.addView(saved);}
    }
    private void manual(){LinearLayout b=dialogBody();EditText address=new EditText(this);address.setTextColor(WHITE);address.setSingleLine();address.setTextDirection(View.TEXT_DIRECTION_LTR);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setHint("192.168.1.50");address.setText(host);address.setContentDescription("عنوان التلفاز");b.addView(address);
        EditText remotePort=new EditText(this);remotePort.setTextColor(WHITE);remotePort.setInputType(InputType.TYPE_CLASS_NUMBER);remotePort.setSingleLine();remotePort.setText(String.valueOf(port));remotePort.setContentDescription("منفذ الريموت");b.addView(remotePort);TextView help=text("منفذ خدمة الريموت المعتاد 6466. لا تحتاج إلى تفعيل ADB.",12,MUTED);b.addView(help);
        AlertDialog d=show(new AlertDialog.Builder(this).setTitle("عنوان التلفاز").setView(b).setPositiveButton("اتصال",null).setNegativeButton("إلغاء",null).create());
        d.getButton(-1).setOnClickListener(v->{String value=address.getText().toString().trim();int number;try{number=Integer.parseInt(remotePort.getText().toString());}catch(Exception error){number=0;}
            if(value.length()>253||!value.matches("[A-Za-z0-9.:_-]+")||number<1||number>65534){address.setError("أدخل عنوانًا صحيحًا ومنفذًا بين 1 و65534");return;}d.dismiss();select(value,number,"تلفازي");});
    }
    private String pinKey(){return "pin:"+host+":"+port;}
    private void select(String address,int value,String label){if(remote!=null)remote.disconnect();connecting=false;host=address;port=value;name=label;deviceName.setText(label);prefs.edit().putString("host",host).putInt("port",port).putString("name",name).apply();connectSaved();}
    private void connectSaved(){if(remote==null){toast("جارٍ تجهيز الاتصال؛ حاول بعد لحظة");return;}if(connecting)return;connecting=true;setConnected(false);String stored=prefs.getString(pinKey(),null);byte[] pin=stored==null?null:android.util.Base64.decode(stored,0);remote.connect(host,port,pin);}
    private void pairing(){if(!active||isFinishing())return;if(codeDialog!=null&&codeDialog.isShowing())return;
        LinearLayout b=dialogBody();TextView hint=text("أدخل الرمز المكوّن من ست خانات الظاهر على التلفاز. قد يتضمن حروفًا من A إلى F.",14,MUTED);b.addView(hint);
        EditText input=new EditText(this);input.setTextColor(WHITE);input.setTextSize(26);input.setSingleLine();input.setGravity(Gravity.CENTER);input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);input.setContentDescription("رمز اقتران التلفاز");b.addView(input);
        codeDialog=show(new AlertDialog.Builder(this).setTitle("رمز الاقتران").setView(b).setPositiveButton("ربط التلفاز",null).setNegativeButton("إلغاء",(d,w)->{remote.disconnect();connecting=false;connection.setText("أُلغي الاقتران");}).create());
        codeDialog.setCanceledOnTouchOutside(false);codeDialog.getButton(-1).setOnClickListener(v->{String value=input.getText().toString().trim();if(!value.matches("(?i)[0-9a-f]{6}")){input.setError("الرمز ست خانات");return;}codeDialog.getButton(-1).setEnabled(false);remote.submitCode(value);});
        codeDialog.setOnCancelListener(v->{remote.disconnect();connecting=false;connection.setText("أُلغي الاقتران");});
    }
    private void keyboard(){if(!connected())return;if(!remote.hasTextField()){toast("افتح حقل كتابة أو بحث على التلفاز أولًا");return;}LinearLayout b=dialogBody();EditText input=new EditText(this);input.setTextColor(WHITE);input.setHint("النص المراد إرساله");input.setContentDescription("نص للتلفاز");b.addView(input);
        AlertDialog d=show(new AlertDialog.Builder(this).setTitle("الكتابة على التلفاز").setView(b).setPositiveButton("إرسال",null).setNegativeButton("إلغاء",null).create());d.getButton(-1).setOnClickListener(v->{if(!remote.hasTextField()){toast("أُغلق حقل الكتابة؛ افتحه على التلفاز مجددًا");return;}String value=input.getText().toString();if(value.isEmpty())return;remote.text(value);d.dismiss();});
    }
    private void settings(){LinearLayout b=dialogBody();final AlertDialog[] d={null};b.addView(item("الخلفيات والصور",()->{d[0].dismiss();gallery();}));gap(b,8);b.addView(item("القرد داخل التطبيق",()->{d[0].dismiss();monkeySettings();}));gap(b,8);b.addView(item("إعادة الاقتران بالتلفاز",()->{d[0].dismiss();if(host.isEmpty()){devices();return;}new AlertDialog.Builder(this).setTitle("إعادة الاقتران؟").setMessage("سيظهر رمز جديد على التلفاز لتأكيد الاتصال.").setPositiveButton("متابعة",(x,y)->{prefs.edit().remove(pinKey()).apply();remote.disconnect();connecting=false;connectSaved();}).setNegativeButton("إلغاء",null).show();}));gap(b,8);b.addView(item("المصادر وحول التطبيق",()->{d[0].dismiss();about();}));d[0]=dialog("الإعدادات",b);}
    private void monkeySettings(){LinearLayout b=dialogBody();Switch enabled=new Switch(this);enabled.setText("ظهور القرد داخل التطبيق");enabled.setTextColor(WHITE);enabled.setChecked(prefs.getBoolean("monkey",true));enabled.setOnCheckedChangeListener((v,value)->prefs.edit().putBoolean("monkey",value).apply());b.addView(enabled);gap(b,12);
        TextView value=text("نسبة الظهور في كل ساعة: "+prefs.getInt("monkey_chance",2)+"%",15,WHITE);b.addView(value);SeekBar chance=new SeekBar(this);chance.setMax(100);chance.setProgress(prefs.getInt("monkey_chance",2));chance.setContentDescription("نسبة ظهور القرد");chance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){prefs.edit().putInt("monkey_chance",v.getProgress()).apply();}public void onProgressChanged(SeekBar v,int p,boolean from){value.setText("نسبة الظهور في كل ساعة: "+p+"%");}});b.addView(chance);
        TextView note=text("الافتراضي 2% مع فرصة واحدة في الساعة أثناء فتح التطبيق. القرد لا يظهر فوق التطبيقات الأخرى.",12,MUTED);b.addView(note);gap(b,12);final AlertDialog[] d={null};b.addView(item("جرّب القرد الآن",()->{d[0].dismiss();ui.postDelayed(()->{if(active)monkey.play();},180);}));d[0]=dialog("القرد المشاغب",b);}
    private void gallery(){LinearLayout b=dialogBody();final AlertDialog[] d={null};b.addView(item("تغيير الصور عشوائيًا",()->{prefs.edit().putInt("photo",-1).apply();d[0].dismiss();loadPhoto(true);}));gap(b,12);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(2);ScrollView list=new ScrollView(this);list.addView(grid);b.addView(list,new LinearLayout.LayoutParams(-1,dp(360)));d[0]=dialog("صور عُمان",b);
        int width=(getResources().getDisplayMetrics().widthPixels-dp(100))/2;
        for(int i=0;i<photos.size();i++){final int index=i;LinearLayout tile=column();tile.setPadding(dp(4),dp(4),dp(4),dp(8));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(photos.get(i).name);tile.addView(image,new LinearLayout.LayoutParams(width,dp(82)));TextView title=text(photos.get(i).name,11,WHITE);title.setSingleLine();tile.addView(title);tile.setOnClickListener(v->{prefs.edit().putInt("photo",index).apply();d[0].dismiss();loadPhoto(true);});grid.addView(tile);workers.execute(()->{Bitmap thumb=PhotoCatalog.thumbnail(this,photos.get(index).asset);ui.post(()->{if(d[0].isShowing())image.setImageBitmap(thumb);});});}
    }
    private void about(){LinearLayout b=dialogBody();TextView title=text("أُفق ريموت 1.0.0\nM7MD929\n\nريموت محلي لأجهزة Android TV وGoogle TV عبر خدمة Remote v2. لا يحتاج حسابًا أو ADB، ولا يحتوي إعلانات. لا يشغّل التلفاز إذا كان مفصولًا من الكهرباء أو لا يستقبل الشبكة.\n\nخلفيات عُمان والقرد من مشروع أُفق. القرد يظهر داخل هذا التطبيق فقط.\n\nتنفيذ البروتوكول مستند إلى مخططات androidtvremote2 بترخيص Apache-2.0.\n\nمصادر الصور:\n",14,WHITE);
        try(InputStream in=getAssets().open("credits.txt")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);title.append(bytes.toString("UTF-8"));}catch(IOException ignored){}
        ScrollView scroll=new ScrollView(this);scroll.addView(title);b.addView(scroll,new LinearLayout.LayoutParams(-1,dp(380)));dialog("حول أُفق ريموت",b);}
    private class Events implements RemoteSession.Listener {
        public void status(String value){ui.post(()->{connection.setText(value);if(codeDialog!=null&&codeDialog.isShowing()){codeDialog.getButton(-1).setEnabled(true);}});}
        public void codeNeeded(){ui.post(()->{if(codeDialog!=null&&codeDialog.isShowing()){codeDialog.getButton(-1).setEnabled(true);toast(connection.getText().toString());}else pairing();});}
        public void paired(byte[] pin){String key=pinKey();prefs.edit().putString(key,android.util.Base64.encodeToString(pin,android.util.Base64.NO_WRAP)).apply();ui.post(()->{if(codeDialog!=null)codeDialog.dismiss();});}
        public void ready(){ui.post(()->{connecting=false;if(active&&remote!=null&&remote.isReady())setConnected(true);});}
        public void disconnected(String reason){android.util.Log.w("UfuqRemote",reason);ui.post(()->{connecting=false;setConnected(false);connection.setText("غير متصل · اضغط للمحاولة");if(codeDialog!=null)codeDialog.dismiss();if(active)toast(reason);});}
        public void textAvailable(boolean available){}
    }
    @Override protected void onResume(){super.onResume();active=true;ui.removeCallbacks(rotate);ui.postDelayed(rotate,120000);if(nextMonkeyCheck==0)nextMonkeyCheck=System.currentTimeMillis()+3600000;ui.removeCallbacks(prank);ui.postDelayed(prank,10000);if(remote!=null&&!host.isEmpty())connectSaved();}
    @Override protected void onPause(){active=false;repeatKey=-1;ui.removeCallbacks(repeat);ui.removeCallbacks(rotate);ui.removeCallbacks(prank);if(monkey!=null)monkey.stop();discovery.stop();if(remote!=null)remote.disconnect();connecting=false;setConnected(false);connection.setText("غير متصل · اضغط للاتصال");if(codeDialog!=null)codeDialog.dismiss();super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);if(remote!=null)remote.shutdown();discovery.stop();workers.shutdownNow();super.onDestroy();}
    private class Glass extends Drawable {
        final View owner;final int radius,tint;final Paint paint=new Paint(3);final RectF rect=new RectF();
        Glass(View owner,int radius){this(owner,radius,0x22FFFFFF);}Glass(View owner,int radius,int tint){this.owner=owner;this.radius=dp(radius);this.tint=tint;}
        public void draw(Canvas canvas){rect.set(getBounds());canvas.save();Path path=new Path();path.addRoundRect(rect,radius,radius,Path.Direction.CW);canvas.clipPath(path);
            if(blurred!=null&&!blurred.isRecycled()){int[] loc=new int[2],base=new int[2];owner.getLocationOnScreen(loc);root.getLocationOnScreen(base);float scale=Math.max((float)root.getWidth()/blurred.getWidth(),(float)root.getHeight()/blurred.getHeight());Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate((root.getWidth()-blurred.getWidth()*scale)/2-loc[0]+base[0],(root.getHeight()-blurred.getHeight()*scale)/2-loc[1]+base[1]);paint.setAlpha(255);paint.setShader(null);canvas.drawBitmap(blurred,matrix,paint);paint.setColor(0x59111F27);canvas.drawRect(rect,paint);}
            paint.setColor(tint);paint.setShader(new LinearGradient(0,0,rect.width(),rect.height(),new int[]{tint,0x1638474A},null,Shader.TileMode.CLAMP));canvas.drawRect(rect,paint);paint.setShader(null);canvas.restore();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x52FFFFFF);rect.inset(dp(.5f),dp(.5f));canvas.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
        }
        public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        public void getOutline(Outline outline){outline.setRoundRect(getBounds(),radius);}
    }
    private class ButtonBackground extends Glass {
        final String icon;final boolean bare;ButtonBackground(View view,String icon){this(view,icon,false);}ButtonBackground(View view,String icon,boolean bare){super(view,18);this.icon=icon;this.bare=bare;}
        public void draw(Canvas canvas){if(!bare)super.draw(canvas);if(owner instanceof TextView&&((TextView)owner).length()>0)return;
            float x=getBounds().exactCenterX(),y=getBounds().exactCenterY(),u=dp(10);Paint p=new Paint(3);p.setColor(WHITE);p.setStrokeWidth(dp(1.8f));p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);canvas.save();canvas.translate(x,y);Path path=new Path();
            switch(icon){
                case "up":path.moveTo(-u*.6f,u*.3f);path.lineTo(0,-u*.4f);path.lineTo(u*.6f,u*.3f);break;
                case "down":path.moveTo(-u*.6f,-u*.3f);path.lineTo(0,u*.4f);path.lineTo(u*.6f,-u*.3f);break;
                case "left":path.moveTo(u*.3f,-u*.6f);path.lineTo(-u*.4f,0);path.lineTo(u*.3f,u*.6f);break;
                case "right":path.moveTo(-u*.3f,-u*.6f);path.lineTo(u*.4f,0);path.lineTo(-u*.3f,u*.6f);break;
                case "home":path.moveTo(-u,u*.1f);path.lineTo(0,-u*.8f);path.lineTo(u,u*.1f);path.moveTo(-u*.65f,-u*.2f);path.lineTo(-u*.65f,u*.8f);path.lineTo(u*.65f,u*.8f);path.lineTo(u*.65f,-u*.2f);break;
                case "back":path.moveTo(-u*.2f,-u*.7f);path.lineTo(u*.5f,0);path.lineTo(-u*.2f,u*.7f);path.moveTo(u*.5f,0);path.lineTo(-u,0);break;
                case "menu":for(int i=-1;i<=1;i++)canvas.drawLine(-u,i*u*.65f,u,i*u*.65f,p);break;
                case "volumeup":case "volumedown":canvas.drawLine(-u,0,u,0,p);if(icon.equals("volumeup"))canvas.drawLine(0,-u,0,u,p);break;
                case "mute":path.moveTo(-u, -u*.3f);path.lineTo(-u*.5f,-u*.3f);path.lineTo(0,-u*.8f);path.lineTo(0,u*.8f);path.lineTo(-u*.5f,u*.3f);path.lineTo(-u,u*.3f);path.close();canvas.drawLine(u*.4f,-u*.35f,u, u*.35f,p);canvas.drawLine(u,-u*.35f,u*.4f,u*.35f,p);break;
                case "play":path.moveTo(-u*.7f,-u*.8f);path.lineTo(u*.1f,0);path.lineTo(-u*.7f,u*.8f);path.close();canvas.drawLine(u*.65f,-u*.7f,u*.65f,u*.7f,p);break;
                case "input":canvas.drawRoundRect(-u,-u*.7f,u,u*.7f,dp(2),dp(2),p);canvas.drawLine(-u*.5f,u,u*.5f,u,p);path.moveTo(-u*.4f,0);path.lineTo(u*.4f,0);path.lineTo(0,-u*.35f);path.moveTo(u*.4f,0);path.lineTo(0,u*.35f);break;
                case "keyboard":canvas.drawRoundRect(-u*1.2f,-u*.7f,u*1.2f,u*.7f,dp(2),dp(2),p);for(int i=-1;i<=1;i++){canvas.drawPoint(i*u*.65f,-u*.2f,p);}canvas.drawLine(-u*.6f,u*.3f,u*.6f,u*.3f,p);break;
                case "settings":canvas.drawCircle(0,0,u*.65f,p);canvas.drawCircle(0,0,u*.2f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine((float)Math.cos(a)*u*.75f,(float)Math.sin(a)*u*.75f,(float)Math.cos(a)*u,(float)Math.sin(a)*u,p);}break;
                case "power":canvas.drawArc(-u,-u,u,u,-55,290,false,p);canvas.drawLine(0,-u*1.05f,0,0,p);break;
            }canvas.drawPath(path,p);canvas.restore();
        }
    }
}
