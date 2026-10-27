package om.ufuq.remote;
import java.net.*;
import java.util.*;

final class WakeOnLan {
    static byte[] packet(String mac){
        String hex=mac.replace(":","").replace("-","").trim();if(!hex.matches("(?i)[0-9a-f]{12}"))throw new IllegalArgumentException("أدخل عنوان MAC صحيحًا");
        byte[] address=new byte[6];for(int i=0;i<6;i++)address[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        boolean zero=true;for(byte b:address)zero&=b==0;if(zero||(address[0]&1)!=0)throw new IllegalArgumentException("عنوان MAC ليس عنوان جهاز صالحًا");
        byte[] data=new byte[102];Arrays.fill(data,0,6,(byte)255);for(int i=0;i<16;i++)System.arraycopy(address,0,data,6+6*i,6);return data;
    }
    static void send(String mac)throws Exception {
        byte[] data=packet(mac);Set<InetAddress> destinations=new HashSet<>();destinations.add(InetAddress.getByName("255.255.255.255"));
        Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()){NetworkInterface network=interfaces.nextElement();if(!network.isUp()||network.isLoopback())continue;
            for(InterfaceAddress address:network.getInterfaceAddresses())if(address.getBroadcast()!=null)destinations.add(address.getBroadcast());}
        try(DatagramSocket socket=new DatagramSocket()){socket.setBroadcast(true);for(InetAddress target:destinations)for(int port:new int[]{9,7})socket.send(new DatagramPacket(data,data.length,target,port));}
    }
}
