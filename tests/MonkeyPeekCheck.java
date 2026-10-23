package om.quiet.tv;
public final class MonkeyPeekCheck {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static boolean outside(float x,float y,int width,int height){return x<=0||x>=width||y<=0||y>=height;}
    private static void croppedEdgeOutside(MonkeyMotion.Pose p,boolean rightEdge,int width,int height){
        float x=rightEdge?p.width/2:0,y=rightEdge?0:p.height/2;
        if(p.mirror)x=-x;double angle=Math.toRadians(p.angle);
        float mappedX=p.x+p.width/2+(float)(x*Math.cos(angle)-y*Math.sin(angle));
        float mappedY=p.y+p.height/2+(float)(x*Math.sin(angle)+y*Math.cos(angle));
        check(outside(mappedX,mappedY,width,height),"Cropped torso edge stays outside screen");
    }
    public static void main(String[] args){
        MonkeyMotion.Pose p=new MonkeyMotion.Pose();
        for(int[] size:new int[][]{{736,414},{1920,1080},{3840,2160}})for(int edge=0;edge<4;edge++)for(float anchor:new float[]{0,.25f,.5f,.75f,1}){
            int width=size[0],height=size[1];
            for(long elapsed=0;elapsed<=1280;elapsed+=8){
                MonkeyMotion.sample(MonkeyMotion.PEEK,edge,anchor,elapsed,width,height,p);
                croppedEdgeOutside(p,true,width,height);croppedEdgeOutside(p,false,width,height);
                if(elapsed==0||elapsed==1280)check(p.x+p.width<=.01f||p.x>=width-.01f||p.y+p.height<=.01f||p.y>=height-.01f,"Fully enters/exits beyond the frame");
            }
            MonkeyMotion.sample(MonkeyMotion.PEEK,edge,anchor,600,width,height,p);
            boolean left=p.x<0&&p.x+p.width>0,right=p.x<width&&p.x+p.width>width;
            boolean top=p.y<0&&p.y+p.height>0,bottom=p.y<height&&p.y+p.height>height;
            check((left||right)&&(top||bottom),"Peek always attached to an actual corner");
            check(p.angle==0||p.angle==180,"Correct upright or hanging orientation");
        }
        System.out.println("PASS: four edges, all corners, three resolutions; both cropped edges hidden and no floating torso.");
    }
}
