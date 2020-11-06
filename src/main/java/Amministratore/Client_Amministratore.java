package Amministratore;

import Admin_Server.MessaggiAdminServer;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;


public class Client_Amministratore {

    private String ip;
    private int port;
    private ArrayList<StreamObserver<MessaggiAdminServer.receiveNotification>> streams = new ArrayList<>();

    public Client_Amministratore(String address, int door) {
        this.ip = address;
        this.port = door;
    }

    public synchronized void addStream(StreamObserver<MessaggiAdminServer.receiveNotification> str){
        if(!streams.contains(str)){
            streams.add(str);
        }
    }

    public synchronized ArrayList<StreamObserver<MessaggiAdminServer.receiveNotification>> getStream(){
        return new ArrayList<>(streams);
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
