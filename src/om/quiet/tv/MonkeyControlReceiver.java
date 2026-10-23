package om.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.provider.Settings;

/** Only the authorized ADB shell/platform can send commands (DUMP permission). */
public final class MonkeyControlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!"om.quiet.tv.monkey.CONTROL".equals(intent.getAction()))return;
        String command=intent.getStringExtra("command");
        if("stop".equals(command)){
            MonkeyService.stop(context);setResultCode(Activity.RESULT_OK);setResultData("stopped");
        }else if("random".equals(command)||"demo".equals(command)){
            if(!Settings.canDrawOverlays(context)){setResultCode(Activity.RESULT_CANCELED);setResultData("overlay permission required");return;}
            MonkeyService.start(context,"random".equals(command)?MonkeyService.RANDOM:MonkeyService.DEMO);
            setResultCode(Activity.RESULT_OK);setResultData(command+" started");
        }else{setResultCode(Activity.RESULT_CANCELED);setResultData("unknown command");}
    }
}
