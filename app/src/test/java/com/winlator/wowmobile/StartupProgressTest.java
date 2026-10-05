package com.winlator.wowmobile;

public final class StartupProgressTest {
    public static void main(String[] args) {
        require(StartupProgress.estimate(0,48_000)==0,"starts at zero");
        require(StartupProgress.estimate(-1,48_000)==0,"negative elapsed time");
        require(StartupProgress.estimate(24_000,48_000)==4500,"half previous startup reaches forty-five percent");
        require(StartupProgress.estimate(48_000,48_000)==9000,"previous startup duration reaches ninety percent");
        require(StartupProgress.estimate(24_000,96_000)==2250,"longer history changes the next curve");
        require(StartupProgress.estimate(30_000,0)==4500,"invalid history uses sixty-second default");
        int later=StartupProgress.estimate(49_000,48_000);
        require(later>9000 && later<9100,"slower startup continues smoothly");
        int previous=0;
        for (long elapsed=0;elapsed<=3_600_000;elapsed+=1000) {
            int value=StartupProgress.estimate(elapsed,48_000);
            require(value>=previous && value<=9950,"monotonic and never falsely complete");
            previous=value;
        }
        require(StartupProgress.estimate(Long.MAX_VALUE,48_000)==9950,"long waits remain below completion");
        require("00:09".equals(StartupProgress.elapsedText(9_999)),"seconds format");
        require("01:00".equals(StartupProgress.elapsedText(60_000)),"minute rollover");
        require("02:01".equals(StartupProgress.elapsedText(121_000)),"long wait format");
        require(!StartupProgress.showManualReveal(120_000),"no manual reveal before or at 120 seconds");
        require(StartupProgress.showManualReveal(120_001),"manual reveal after 120 seconds");
        System.out.println("PASS: history-based smooth progress, timer rollover, 120-second reveal, completion reserved");
    }
    private static void require(boolean condition,String label) {
        if (!condition) throw new AssertionError(label);
    }
}
