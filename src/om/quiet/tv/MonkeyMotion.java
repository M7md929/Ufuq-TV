package om.quiet.tv;

/** Pure geometry, shared by the overlay renderer and host-side boundary checks. */
final class MonkeyMotion {
    static final int HORIZONTAL=0, VERTICAL=1, PEEK=2;
    static final int LEFT=0, RIGHT=1, TOP=2, BOTTOM=3;
    static final class Pose {
        float x,y,width,height,angle;
        int sprite;
        boolean mirror;
    }
    static long duration(int kind){return kind==PEEK?1280:kind==HORIZONTAL?720:660;}
    static void sample(int kind,int variant,float anchor,long elapsed,int width,int height,Pose out){
        float u=Math.max(0,Math.min(1,elapsed/(float)duration(kind)));
        out.sprite=kind==PEEK?1:0;out.mirror=false;out.angle=0;
        out.height=height*(kind==PEEK?.43f:.53f);
        out.width=out.height*(kind==PEEK?513f/835f:644f/1002f);
        float w=out.width,h=out.height;
        if(kind==HORIZONTAL){
            float start=-w-width*.04f,end=width+width*.04f;
            out.x=variant==0?start+(end-start)*u:end+(start-end)*u;
            out.y=(height-h)*anchor-(float)Math.sin(u*Math.PI)*height*.025f;
            out.mirror=variant==1;out.angle=(variant==0?1:-1)*(float)Math.sin(u*Math.PI)*7;
        }else if(kind==VERTICAL){
            float bottom=height+height*.04f,top=-h-height*.04f;
            out.x=width*anchor-w/2+(float)Math.sin(u*Math.PI)*width*.015f;
            out.y=variant==0?bottom+(top-bottom)*u:top+(bottom-top)*u;
            out.angle=(variant==0?1:-1)*(float)Math.sin(u*Math.PI)*6;
        }else{
            float reveal,escape=0;
            if(elapsed<240){float enter=elapsed/240f;reveal=1-(float)Math.pow(1-enter,3);}
            else if(elapsed<1120)reveal=1;
            else{escape=Math.max(0,Math.min(1,(elapsed-1120)/160f));reveal=1-escape*escape;}
            // This pose is cropped on its right and bottom. Keep BOTH crop edges
            // outside the screen, so a torso can never float in the middle of an edge.
            boolean side=variant==LEFT||variant==RIGHT;
            boolean top=side?anchor<.5f:variant==TOP;
            boolean right=side?variant==RIGHT:anchor>=.5f;
            out.angle=top?180:0;out.mirror=top?right:!right;
            float exposedWidth=w*.82f,exposedHeight=h*.64f;
            if(side){
                out.x=right?width-exposedWidth*reveal:-w+exposedWidth*reveal;
                out.y=top?-h+exposedHeight:height-exposedHeight;
            }else{
                out.x=right?width-exposedWidth:-w+exposedWidth;
                out.y=top?-h+exposedHeight*reveal:height-exposedHeight*reveal;
            }
        }
    }
}
