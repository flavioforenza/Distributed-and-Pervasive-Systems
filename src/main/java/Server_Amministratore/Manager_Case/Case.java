package Server_Amministratore.Manager_Case;

import Admin_Server.MessaggiAdminServer;
import Server_Amministratore.Manager_Admin.AdminServer;
import Server_Amministratore.Manager_Admin.Admins;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import Admin_Server.AdminServerGrpc;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.lang.reflect.Array;
import java.util.*;


@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Case {

    @XmlElement(name="case")
    private Map<Integer, CasaServer>listaCase;

    private static Case instance;

    public Case(){
        listaCase = new HashMap<>();
    }

    public synchronized static Case getInstance(){
        if(instance==null)
            instance = new Case();
        return instance;
    }

    public synchronized List<CasaServer> getCaseList(){
        //creo una copia
        List <CasaServer> listCase = new ArrayList<>(listaCase.values());
        return listCase;
    }

    public synchronized boolean add(CasaServer u){
        boolean resultID = controlID(u.getID());
        boolean resultPort = controlPort(u.getPorta());
        boolean outcome=false;
        if (!resultID && !resultPort){
            listaCase.put(u.getID(), u);
            System.out.println("Casa aggiunta: ID = " + u.getID());
            sendNotify(u.getID(), "è stata aggiunta");
        }

        if(resultID)
            System.out.println("Errore durante l'inserimento di una casa: ID già esistente.");

        if(resultPort)
            System.out.println("Errore durante l'inserimento di una casa: Porta già esistente.");


        if(resultID || resultPort)
            outcome = true;

        return outcome;
    }

    public synchronized void removeCasa(int id){
        ArrayList<Integer> key = new ArrayList<>(listaCase.keySet());
        for(int i =0; i<key.size(); i++){
            if(key.get(i)==id){
                listaCase.remove(key.get(i));
                System.out.println("Casa eliminata ID: " + id);
                sendNotify(id, "è stata rimossa");
            }
        }
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

    public boolean controlID(int ID){
        Map<Integer, CasaServer> casaCopy = new HashMap<>(listaCase);
        boolean result = false;
        for(Integer i: casaCopy.keySet())
            if(i == ID)
                result = true;
        return result;
    }

    public boolean controlPort(int port){
        ArrayList<CasaServer> listaCasePort = new ArrayList<>(listaCase.values());
        boolean result = false;
        for(CasaServer casa: listaCasePort){
            if(casa.getPorta()==port){
                result=true;
            }
        }
        return result;
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
