package Server_Amministratore.Manager_Stat_Boost;

import Admin_Server.AdminServerGrpc;
import Admin_Server.MessaggiAdminServer;
import Server_Amministratore.Manager_Admin.AdminServer;
import Server_Amministratore.Manager_Admin.Admins;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Boost {

    @XmlElement(name="boost")
    private ArrayList<Integer> requestBoost;

    public static Boost instance;

    public Boost(){
        requestBoost = new ArrayList<>();
    }

    public synchronized static Boost getInstance(){
        if(instance==null)
            instance = new Boost();
        return instance;
    }

    public synchronized void addRequestBoost(int id){
        requestBoost.add(id);
        System.out.println("Richiesta boost, casa ID: " + id);
        sendNotify(id, "ha richiesto il Boost.");
    }

    public synchronized void sendNotify(int id, String operation){
        ArrayList<AdminServer> listAdmnin = new ArrayList<>(Admins.getInstance().getAmministratori());
        String value = "La casa con ID " + id + " " + operation;
        for(int k=0; k<listAdmnin.size(); k++){
            try {
                sendNotification(listAdmnin.get(k), value);
            } catch (InterruptedException e) {
                System.out.println("Client amministratore non raggiungibile");
                e.printStackTrace();
            }
        }
    }

    public static void sendNotification(AdminServer client, String value) throws InterruptedException{

        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+ client.getPortAdmin()).usePlaintext(true).build();

        Admins.getInstance().addChannel(client, channel);

        AdminServerGrpc.AdminServerStub stub = AdminServerGrpc.newStub(channel);

        MessaggiAdminServer.sendNotification request = MessaggiAdminServer.sendNotification.newBuilder()
                .setNotification(value).build();

        StreamObserver<MessaggiAdminServer.sendNotification> streamNotification = stub.streamNotify(new StreamObserver<MessaggiAdminServer.receiveNotification>() {
            @Override
            public void onNext(MessaggiAdminServer.receiveNotification value) {
                System.out.println("Risposta: " + value.getResponseNotification());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Un admin si è disconnesso improvvisamente");
                //rimozione admin + chiusura stream & canale
                Admins.getInstance().removeAdmin(client);
            }

            @Override
            public void onCompleted() {
                //channel.shutdown();
            }
        });

        streamNotification.onNext(request);

        //salvo lo stream associato all'admin
        Admins.getInstance().addStreams(client, streamNotification);
    }


}
