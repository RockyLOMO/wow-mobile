package com.winlator.wowmobile;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;

/** Device smoke test against real foreground-service lifecycle and production Range downloads. */
public final class BootstrapSmokeInstrumentation extends Instrumentation {
    private boolean preview;
    @Override public void onCreate(Bundle args) { super.onCreate(args); preview=args!=null && "true".equals(args.getString("preview")); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if (!"1520x720".equals(OldDreamIntegration.resolution(getTargetContext()))) throw new AssertionError("S10 resolution");
            if(!ClientInstaller.isClientReady(new File("/storage/emulated/0/WoW335CN"))) throw new AssertionError("existing client detection");
            File partial=new File(ClientInstallService.cache(),"client.zip.part");
            long first=partial.length();
            long bytes=runUntilProgress(first);
            if(bytes<=first) throw new AssertionError("download did not advance");
            long resumed=runUntilProgress(bytes);
            if(resumed<=bytes) throw new AssertionError("resume did not advance");
            if(ClientInstallService.snapshot.running)throw new AssertionError("worker did not stop");
            result.putString("stream","PASS: S10 1520x720, existing client detection, Android foreground notification, production download/pause/resume: "+bytes+" -> "+resumed+" bytes.\n");
            finish(-1,result);
        }catch(Throwable error) {
            getTargetContext().startService(new Intent(getTargetContext(),ClientInstallService.class).setAction(ClientInstallService.PAUSE));
            result.putString("stream","FAIL: "+error+"\n");finish(0,result);
        }
    }
    private long runUntilProgress(long prior)throws Exception {
        getTargetContext().startForegroundService(new Intent(getTargetContext(),ClientInstallService.class));
        if (preview) {
            preview=false;
            getTargetContext().startActivity(new Intent(getTargetContext(),WowMobileActivity.class)
                .putExtra("skip_auto_launch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(15000);
        }
        long timeout=SystemClock.elapsedRealtime()+45000;
        File partial=new File(ClientInstallService.cache(),"client.zip.part");
        boolean active=false;
        while(SystemClock.elapsedRealtime()<timeout) {
            ClientInstallService.Snapshot state=ClientInstallService.snapshot;
            active|=state.running;
            if(partial.length()>prior+1024*1024)break;
            if(active&&!state.running)throw new AssertionError(state.detail);
            SystemClock.sleep(100);
        }
        getTargetContext().startService(new Intent(getTargetContext(),ClientInstallService.class).setAction(ClientInstallService.PAUSE));
        timeout=SystemClock.elapsedRealtime()+20000;
        while(ClientInstallService.snapshot.running&&SystemClock.elapsedRealtime()<timeout)SystemClock.sleep(100);
        if(ClientInstallService.snapshot.running)throw new AssertionError("pause timeout");
        SystemClock.sleep(300); return partial.length();
    }
}
