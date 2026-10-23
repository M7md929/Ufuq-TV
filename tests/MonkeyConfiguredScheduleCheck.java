package om.quiet.tv;
public final class MonkeyConfiguredScheduleCheck {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int chance=0;chance<=100;chance++){
            int count=0;for(int roll=0;roll<100;roll++)if(MonkeySchedule.shouldAppear(roll,chance))count++;
            check(count==chance,"Exact configured probability: "+chance);
        }
        for(int attempts=1;attempts<=60;attempts++){
            long interval=MonkeySchedule.interval(attempts),now=10000000L;
            check(interval==3600000L/attempts,"Even opportunity spacing");
            check(MonkeySchedule.nextDue(now,0,interval)==now+interval,"Full first interval");
            check(MonkeySchedule.nextDue(now,now+1000,interval)==now+1000,"Preserve pending check");
            check(MonkeySchedule.nextDue(now,now-1000,interval)==now+interval,"No catch-up burst");
        }
        check(MonkeySchedule.interval(4)==900000L,"Four chances per hour");
        check(MonkeySchedule.chance(-1)==0&&MonkeySchedule.chance(150)==100,"Chance limits");
        check(MonkeySchedule.attempts(0)==1&&MonkeySchedule.attempts(100)==60,"Opportunity limits");
        System.out.println("PASS: every probability 0–100%, counts 1–60/hour, spacing and restart boundaries.");
    }
}
