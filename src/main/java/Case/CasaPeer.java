package Case;


import Server_Amministratore.Manager_Case.CasaServer;
import Smart_Meter.Measurement;

import Smart_Meter.SmartMeterSimulator;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import subpackage.MessaggiHouse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.logging.Logger;

public class CasaPeer {
    //lista delle case presenti nel condominio
    private ArrayList<CasaPeer> listaCase = new ArrayList<>();
    //media delle statistiche della prima casa
    private Measurement mediaPrimaCasa =null;
    //lista contenente le medie delle altre case
    private ArrayList<Measurement> medieCaseNew = new ArrayList<>();
    //lista contenente le proprie medie (generate dallo sliding windows)
    private ArrayList<Measurement> mediePersonali = new ArrayList<>();
    //lista degli stream aperti
    private HashMap<CasaPeer, StreamObserver<MessaggiHouse.SendStatistics>> streams = new HashMap<>();
    //lista dei canali aperti
    private HashMap<CasaPeer, ManagedChannel> channels = new HashMap<>();
    //lista dei Thread attualmente attivi
    private ArrayList<Thread> threads = new ArrayList<>();
    //richiesta del boost
    private boolean requestBoost = false;
    //utilizzo la risorsa del boost
    private boolean useBoost = false;
    //coda boost
    private HashMap<Integer, StreamObserver<MessaggiHouse.ResponseBoost>> codaBoost = new HashMap<>();
    //ultimo timestamp prodotto
    private long lastTimestamp=0;
    //consensi per il boost
    private int consensi=0;
    //lista thread boost
    private ArrayList<Thread> threadsBoost = new ArrayList<>();
    //SmarteMeter
    private SmartMeterSimulator smart;
    //utile per mandare la notifica alle altre case in coda dopo il boost
    private boolean finishBoost=false;

    private int ID;
    private String IP;
    private int porta;
    private String status="Peer";

    private CasaPeer(){}

    public CasaPeer(int ID, String IP, int porta) {
        this.ID = ID;
        this.IP = IP;
        this.porta = porta;
    }

    /*
     ********* METODI GESTIONE CANALI + STREAM *********
     */

    public synchronized void addChannel(CasaPeer casa, ManagedChannel channel){
        channels.put(casa, channel);
    }

    public synchronized void removeChannel(CasaPeer casa){
        channels.remove(casa);
    }

    public synchronized HashMap<CasaPeer, ManagedChannel> getChannel(){
        return new HashMap<>(channels);
    }

    public synchronized HashMap<CasaPeer, StreamObserver<MessaggiHouse.SendStatistics>> getStreams(){
        return new HashMap<>(streams);
    }

    public synchronized void addStreams(CasaPeer casa, StreamObserver<MessaggiHouse.SendStatistics> stream){
        if(!streams.containsKey(casa)){
            streams.put(casa, stream);
        }
    }

    public synchronized void removeStream(int id){
        ArrayList<CasaPeer> listaCaseStream = new ArrayList<>(streams.keySet());
        for(int i=0; i<listaCaseStream.size(); i++){
            if(listaCaseStream.get(i).getID()==id){
                streams.remove(listaCaseStream.get(i));
            }
        }
    }

    /*
     ********* METODI GESTIONE CASE *********
     */

    public synchronized ArrayList<CasaPeer> getListaCase(){
        return new ArrayList<>(listaCase);
    }

    public synchronized void addCasa(CasaPeer casaPeer){
        listaCase.add(casaPeer);
    }

    public synchronized void removeCasa(int id){
        for(int i =0; i<listaCase.size(); i++){
            if(listaCase.get(i).getID()==id)
                listaCase.remove(i);
        }
    }

    /*
    ********* METODI GESTIONE MEDIE *********
     */

    public void addMediaPrimaCasa(Measurement media){
        mediaPrimaCasa = media;
    }

    public Measurement getmediaPrimaCasa(){
        return mediaPrimaCasa;
    }

    public void resetMediaPrimaCasa(){
        mediaPrimaCasa=null;
    }

    public synchronized void addMediePersonali(Measurement measurement) {
        mediePersonali.add(measurement);
    }

    public synchronized ArrayList<Measurement> getMediePersonali() {
        return new ArrayList<>(mediePersonali);
    }

    public synchronized void addMedie (Measurement measurement){
        medieCaseNew.add(measurement);
        notifyAll();
    }

    public synchronized ArrayList<Measurement> getMedie(){
        ArrayList<Measurement> medieRic = new ArrayList<>();
        while(medieCaseNew.size()==0){
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(medieCaseNew.size()>0){
            medieRic.addAll(medieCaseNew);
        }
        return medieRic;
    }

    /*
    ********* METODI PER IL BOOST *********
     */

    public synchronized void addLastTimestamp(long timestamp){
        lastTimestamp = timestamp;
    }

    public synchronized long getLastTimestamp(){
        long temp = lastTimestamp;
        return temp;
    }

    public long computeMidnightMilliseconds(){
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        lastTimestamp = c.getTimeInMillis();
        return c.getTimeInMillis();
    }

    public void setSmart(SmartMeterSimulator smartMeter){
        smart = smartMeter;
    }

    private void boost() {
        setUseBoost(true);
        System.out.println("Consenso ricevuto dalle case. \n");
        Logger LOGGER = Logger.getLogger(CasaPeer.class.getName());
        LOGGER.info("*********** INIZIO DELLA FASE DI BOOST ***********\n");
        try {
            smart.boost();
        } catch (InterruptedException e) {
            e.getMessage();
        }

        System.out.println("\n");
        LOGGER.info("*********** FINE DELLA FASE DI BOOST ***********\n");

        setUseBoost(false);
        setRequestBoost(false);
        setFinishBoost(true);
    }

    public synchronized boolean isRequestBoost() {
        return requestBoost;
    }

    public synchronized void setRequestBoost(boolean request) {
        requestBoost = request;
    }

    public synchronized boolean isUseBoost() {
        return useBoost;
    }

    public synchronized void setUseBoost(boolean useboost) {
        useBoost = useboost;
    }

    public synchronized void addCodaBoost(int id, StreamObserver<MessaggiHouse.ResponseBoost> responseObserver){
        codaBoost.put(id, responseObserver);
        notifyAll();
    }

    public synchronized HashMap<Integer,StreamObserver<MessaggiHouse.ResponseBoost>> getCodaBoost() throws InterruptedException {
        while(codaBoost.size()==0){
            wait();
        }
        return new HashMap<>(codaBoost);
    }

    public synchronized void removeCodaBoost(int request){
        codaBoost.remove(request);
    }

    public synchronized void setFinishBoost(boolean condition){
        finishBoost = condition;
        notifyAll();
    }

    public synchronized boolean getFinishBoost(){
        while(!finishBoost){
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return finishBoost;
    }

    public synchronized void setConsensi() {
        consensi++;

        if(consensi == getListaCase().size()-2){
            Thread th = new Thread(()->{
                boost();
            });
            th.start();
            addThreadBoost(th);
        }
    }

    public synchronized void resetConsensi(){
        consensi = 0;
        notifyAll();
    }

    public synchronized void addThreadBoost(Thread boost){
        threadsBoost.add(boost);
        notify();
    }

    public synchronized ArrayList<Thread> getThreadsBoost(){
        return new ArrayList<>(threadsBoost);
    }

    public synchronized void removeThreadBoost(Thread tB){
        for(int i =0; i<threadsBoost.size(); i++){
            if(threadsBoost.get(i)==tB){
                threadsBoost.remove(i);
            }
        }
    }

    /*
     ********* METODI VARI *********
     */

    public synchronized int getIDMax(){
        int maxID=0;
        for(int i=0; i<listaCase.size(); i++){
            if(listaCase.get(i).getID()>maxID){
                maxID= listaCase.get(i).getID();
            }
        }
        return maxID;
    }

    public synchronized void setStatus(String stato){
        this.status = stato;
    }

    public synchronized String getStatus(){
        return this.status;
    }

    public int getID() {
        return ID;
    }

    public String getIP() {
        return IP;
    }

    public int getPorta() {
        return porta;
    }

    public void requestToServer(CasaServer casaServer, String HOST, int PORT, String request){
        //rimuovo casa dal Server Amministratore
        ClientConfig clientConfig2 = new DefaultClientConfig();
        clientConfig2.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        Client cliente2 = Client.create(clientConfig2);

        String requestComplete = "/" +request+ "/" + casaServer.getID() + "/";

        WebResource webRes = cliente2.resource("http://" + HOST + ":" + PORT + "/case" + requestComplete);

        ClientResponse response=null;

        if(request.equals("remove")){
            response = webRes.accept("application/json")
                    .type("application/json").delete(ClientResponse.class, casaServer.getID());
        }else{
            //se non è remove è addRequestBoost
            response = webRes.accept("application/json")
                    .type("application/json").post(ClientResponse.class, casaServer.getID());
        }

        if (response.getStatus()!=200){
            throw new RuntimeException("Failed: HTTP error code: "
                    + response.getStatus());
        }
    }
}
