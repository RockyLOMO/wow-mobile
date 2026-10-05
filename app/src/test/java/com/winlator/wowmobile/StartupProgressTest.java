package com.winlator.wowmobile;

public final class StartupProgressTest {
    public static void main(String[] args) {
        require(StartupProgress.estimate(0)==0,"starts at zero");
        require(StartupProgress.estimate(-1)==0,"negative elapsed time");
        require(StartupProgress.estimate(30_000)==8000,"thirty seconds reaches eighty percent");
        int later=StartupProgress.estimate(31_000);
        require(later>8000 && later<8100,"slow eighty-point-something stage");
        int previous=0;
        for (long elapsed=0;elapsed<=3_600_000;elapsed+=1000) {
            int value=StartupProgress.estimate(elapsed);
            require(value>=previous && value<=9950,"monotonic and never falsely complete");
            previous=value;
        }
        require(StartupProgress.estimate(Long.MAX_VALUE)==9950,"long waits remain below completion");
        System.out.println("PASS: time progress, 80.xx stage, monotonic estimate, real-frame completion reserved");
    }
    private static void require(boolean condition,String label) {
        if (!condition) throw new AssertionError(label);
    }
}
