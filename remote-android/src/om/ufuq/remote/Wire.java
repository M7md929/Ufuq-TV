package om.ufuq.remote;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded protobuf wire subset used by Remote v2. Unknown scalar fields are skipped. */
final class Wire {
    static final int MAX_FRAME=1024*1024;
    static class Message {
        private final Map<Integer,Object> fields=new HashMap<>();
        boolean has(int field){return fields.containsKey(field);}
        long number(int field){Object value=fields.get(field);return value instanceof Long?(Long)value:0;}
        byte[] bytes(int field){Object value=fields.get(field);return value instanceof byte[]?(byte[])value:new byte[0];}
        String text(int field){return new String(bytes(field),StandardCharsets.UTF_8);}
        Message child(int field)throws IOException{return parse(bytes(field));}
    }
    static class Builder {
        private final ByteArrayOutputStream out=new ByteArrayOutputStream();
        Builder number(int field,long value){varint(out,field*8);varint(out,value);return this;}
        Builder bytes(int field,byte[] bytes){varint(out,field*8+2);varint(out,bytes.length);out.write(bytes,0,bytes.length);return this;}
        Builder text(int field,String value){return bytes(field,value.getBytes(StandardCharsets.UTF_8));}
        byte[] build(){return out.toByteArray();}
    }
    static Builder b(){return new Builder();}
    static void varint(OutputStream out,long value){
        try{while((value&~127L)!=0){out.write(((int)value&127)|128);value>>>=7;}out.write((int)value);}
        catch(IOException error){throw new IllegalStateException(error);}
    }
    static long readVarint(InputStream in)throws IOException {
        long result=0;
        for(int i=0;i<10;i++){int x=in.read();if(x<0)throw new EOFException();if(i==9&&(x&254)!=0)throw new IOException("Oversized varint");result|=(long)(x&127)<<(7*i);if((x&128)==0)return result;}
        throw new IOException("Malformed varint");
    }
    static byte[] readFrame(InputStream in)throws IOException {
        long length=readVarint(in);if(length<0||length>MAX_FRAME)throw new IOException("Frame exceeds limit");
        byte[] payload=new byte[(int)length];new DataInputStream(in).readFully(payload);return payload;
    }
    static void writeFrame(OutputStream out,byte[] payload)throws IOException {
        if(payload.length>MAX_FRAME)throw new IOException("Frame exceeds limit");varint(out,payload.length);out.write(payload);out.flush();
    }
    static Message parse(byte[] payload)throws IOException {
        if(payload.length>MAX_FRAME)throw new IOException("Message exceeds limit");
        ByteArrayInputStream in=new ByteArrayInputStream(payload);Message message=new Message();
        while(in.available()>0){long tag=readVarint(in);int field=(int)(tag>>>3),type=(int)(tag&7);if(field<=0||field>536870911)throw new IOException("Invalid field");
            if(type==0)message.fields.put(field,readVarint(in));
            else if(type==2){long size=readVarint(in);if(size<0||size>in.available())throw new IOException("Truncated field");byte[] bytes=new byte[(int)size];new DataInputStream(in).readFully(bytes);message.fields.put(field,bytes);}
            else if(type==1||type==5){int count=type==1?8:4;if(in.skip(count)!=count)throw new EOFException();}
            else throw new IOException("Unsupported wire type");
        }return message;
    }
}
