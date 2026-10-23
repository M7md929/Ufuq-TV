package om.quiet.tv;

public final class MonkeyMotionCheck {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        MonkeyMotion.Pose p=new MonkeyMotion.Pose();
        for(int[] size:new int[][]{{1920,1080},{3840,2160},{736,414}}){
            int w=size[0],h=size[1];
            for(int kind=0;kind<3;kind++)for(int direction=0;direction<(kind==2?4:2);direction++){
                long duration=MonkeyMotion.duration(kind);
                for(long t=0;t<=duration;t+=10){
                    MonkeyMotion.sample(kind,direction,.5f,t,w,h,p);
                    check(Float.isFinite(p.x)&&Float.isFinite(p.y)&&p.width>0&&p.height>0,"Finite geometry");
                }
                MonkeyMotion.sample(kind,direction,.5f,0,w,h,p);
                if(kind==0)check(direction==0?p.x+p.width<=0:p.x>=w,"Horizontal starts outside");
                if(kind==1)check(direction==0?p.y>=h:p.y+p.height<=0,"Vertical starts outside");
                MonkeyMotion.sample(kind,direction,.5f,duration,w,h,p);
                if(kind==0)check(direction==0?p.x>=w:p.x+p.width<=0,"Horizontal finishes outside");
                if(kind==1)check(direction==0?p.y+p.height<=0:p.y>=h,"Vertical finishes outside");
                if(kind==2){
                    check(direction==0?p.x+p.width<=.01f:direction==1?p.x>=w-.01f:direction==2?p.y+p.height<=.01f:p.y>=h-.01f,"Peek fully retreats");
                    MonkeyMotion.sample(kind,direction,.5f,600,w,h,p);
                    check(p.x<w&&p.x+p.width>0&&p.y<h&&p.y+p.height>0,"Peek visible at every edge");
                }
            }
        }
        System.out.println("PASS: both horizontal/vertical directions and all four peek edges at three resolutions.");
    }
}
