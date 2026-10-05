package com.winlator.wowmobile;

import java.util.Arrays;

public final class StartupFrameReadinessTest {
    public static void main(String[] args) {
        int[] frame=new int[96*54];
        require(!StartupFrameReadiness.hasScene(frame),"black startup buffer");
        Arrays.fill(frame,0xff000000);
        for (int i=0;i<12;i++) frame[i]=0xffffffff;
        require(!StartupFrameReadiness.hasScene(frame),"cursor is not a game frame");
        Arrays.fill(frame,0xff606060);
        require(!StartupFrameReadiness.hasScene(frame),"uniform initial clear colour");
        for (int i=0;i<frame.length;i++) frame[i]=i%3==0 ? 0xff4488bb : 0xff0c2038;
        require(StartupFrameReadiness.hasScene(frame),"textured login scene");
        for (int i=0;i<frame.length;i++) frame[i]=i%10==0 ? 0xff558899 : 0xff051019;
        require(StartupFrameReadiness.hasScene(frame),"dark game scene with highlights");
        System.out.println("PASS: startup black/cursor/clear rejection, login and dark-scene readiness");
    }
    private static void require(boolean condition,String label) {
        if (!condition) throw new AssertionError(label);
    }
}
