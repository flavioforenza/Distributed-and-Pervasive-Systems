package Server_Amministratore.Manager_Admin;

import Admin_Server.MessaggiAdminServer;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.HashMap;


@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Admins {

    @XmlElement(name="admins")
    private ArrayList<AdminServer> amministratori;
    private HashMap<AdminServer, StreamObserver<MessaggiAdminServer.sendNotification>> streams;
    private HashMap<AdminServer,ManagedChannel> channels;

    private static Admins instance;

    public Admins() {
        amministratori = new ArrayList<>();
        streams = new HashMap<>();
        channels = new HashMap<>();
    }

    public synchronized static Admins getInstance(){
        if(instance==null)
            instance = new Admins();
        return instance;
    }

    public synchronized boolean addAdmin(AdminServer admin){
        boolean result = false;

        for(AdminServer adminS : amministratori){
            if(adminS.getPortAdmin()==admin.getPortAdmin()){
                result = true;
                System.out.println("Admin già presente, inserire un altro Admin.");
            }
        }

        if(!result){
            amministratori.add(admin);
            System.out.println("Admin aggiunto:");
            System.out.println("IP: " + admin.getIpAdmin());
            System.out.println("Porta: " + admin.getPortAdmin());
        }

        return result;
    }

    public synchronized void removeAdmin(AdminServer admin){
        for(int i =0; i<amministratori.size(); i++){
            if(amministratori.get(i).getPortAdmin()==admin.getPortAdmin()){
                amministratori.remove(i);

                //chiudo lo stream e lo rimuovo dalla lista
                if(streams.containsKey(admin)){
                    streams.get(admin).onCompleted();
                    streams.remove(admin);
                }

                //chiudo il canale e lo rimuovo dalla lista
                if(channels.containsKey(admin)){
                    channels.get(admin).shutdown();
                    channels.remove(admin);
                }

                System.out.println("Admin rimosso");
            }else
                System.out.println("Admin non esistente");
        }
    }

    public synchronized ArrayList<AdminServer> getAmministratori(){
        return new ArrayList<>(amministratori);
    }

    public synchronized void addChannel(AdminServer admin,ManagedChannel channel){
        if(!channels.containsKey(admin))
            channels.put(admin, channel);
    }

    public synchronized void addStreams(AdminServer admin, StreamObserver<MessaggiAdminServer.sendNotification> info){
        if(!streams.containsKey(admin))
            streams.put(admin, info);
    }
}
