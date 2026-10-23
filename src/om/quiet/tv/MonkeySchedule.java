package om.quiet.tv;

/** Independent configured draws, evenly spaced through the hour; no catch-up. */
final class MonkeySchedule {
    static final long HOUR_MS=3600000L;
    static final int DEFAULT_CHANCE_PERCENT=2,DEFAULT_ATTEMPTS=1,MAX_ATTEMPTS=60;
    static int chance(int value){return Math.max(0,Math.min(100,value));}
    static int attempts(int value){return Math.max(1,Math.min(MAX_ATTEMPTS,value));}
    static long interval(int count){return HOUR_MS/attempts(count);}
    static boolean shouldAppear(int roll,int chance){return roll>=0&&roll<chance(chance);}
    static long nextDue(long now,long saved,long interval){
        return saved>now&&saved-now<=interval?saved:now+interval;
    }
}
