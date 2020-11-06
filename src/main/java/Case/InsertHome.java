package Case;

import Server_Amministratore.Manager_Case.CasaServer;
import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

import java.util.*;

import Smart_Meter.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import subpackage.GreetingServiceGrpc;
import subpackage.MessaggiHouse;

public class InsertHome extends CustomBuffer {
    private static final String HOST = "localhost";
    private static final int PORT = 1339;
    private static boolean condition = true;
    private static ServerHome service;

    public static void main(String[] args) {
        int id = Integer.parseInt(args[0]);
        String ip = args[1];
        int porta = Integer.parseInt(args[2]);

        CasaPeer casaPeer= new CasaPeer(id, ip, porta);

        try{
            //Lista dal server amministratore
            contactServer(casaPeer);

            //elezione
            election(casaPeer);

            //Inizializzo il Server della casa
            service = new ServerHome(casaPeer);

            /////////////////// INVIO UN MESSAGGIO ALLE CASE ///////////////////
            for(int i = 0; i< casaPeer.getListaCase().size(); i++){
                //escludo se stessa
                if(!(casaPeer.getListaCase().get(i).getID() == casaPeer.getID())){
                    //mando un messaggio sincrono (1 x ogni thread)
                    if(casaPeer.getListaCase().get(i).getID()!= casaPeer.getID()){
                        presentation(casaPeer.getListaCase().get(i), casaPeer);
                    }
                }
            }

            /////////////////// AVVIO LO SMART METER ///////////////////
            CustomBuffer buffer = new CustomBuffer();
            //inizio a riempire il buffer con il thread del Prof
            SmartMeterSimulator smart = new SmartMeterSimulator(buffer);
            smart.start();

            casaPeer.setSmart(smart);

            /////////////////// GENERAZIONE DELLE STATISTICHE ///////////////////
            Statistics(buffer, casaPeer);

            //thread utile per l'uscita e per il boost
            Thread close = new Thread(()->{
                String choice;
                Scanner scanner = new Scanner(System.in);
                do{
                    choice = scanner.nextLine();
                    switch(choice){
                        case "q":
                            exit(casaPeer, smart, service);
                            break;

                        case "b":
                            requestBoost(casaPeer);
                            break;

                        default:
                            System.out.println("Scelta errata, selezionare un nuovo comando.");
                    }
                }while(!choice.equals("q") || !choice.equals("b"));
            });
            close.start();

            //Avvio del server (della casa)
            service.ServerRun(casaPeer);

        }catch (Exception e){
            e.getMessage();
        }
    }

    private static void requestBoost(CasaPeer casaPeer) {
        if(!casaPeer.isUseBoost()){
            System.out.println("Richiesta del Boost!");
            System.out.println("Attendo la conferma dalle altre case...");

            //mi candido per il boost
            casaPeer.setRequestBoost(true);

            //notifico il SERVER dell'utilizzo del boost (utile per la 1 opzionale)
            CasaServer casaServer = new CasaServer(casaPeer.getID(), casaPeer.getIP(),casaPeer.getPorta());
            casaPeer.requestToServer(casaServer, HOST, PORT, "addRequestBoost");

            //creo un nuovo timestamp
            long timestamp = System.currentTimeMillis() - casaPeer.computeMidnightMilliseconds();
            casaPeer.addLastTimestamp(timestamp);

            //azzero i consensi
            casaPeer.resetConsensi();

            //mando un messaggio alle altre case per avere il consenso
            for(int i =0; i<casaPeer.getListaCase().size(); i++){
                if(casaPeer.getListaCase().get(i).getID()!= casaPeer.getID()){
                    if(!casaPeer.isUseBoost()){
                        sendBoostRequest(casaPeer.getListaCase().get(i), casaPeer, timestamp);
                    }
                }
            }

            //thread che avvisa le case in coda quando finisce il boost
            Thread checkCodaBoost = new Thread(()->{
                try{
                    boolean fineBoost = casaPeer.getFinishBoost();
                    if(fineBoost && casaPeer.getCodaBoost().size()>0){
                        //avviso l'intera coda
                        notifyHouse(casaPeer);
                        //termino i thread impiegati per il boost
                        ArrayList<Thread> threadBoost = new ArrayList<>(casaPeer.getThreadsBoost());
                        for(Thread tB : threadBoost){
                            tB.join();
                            casaPeer.removeThreadBoost(tB);
                        }
                    }
                    casaPeer.setFinishBoost(false);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            checkCodaBoost.start();
        }else
            System.out.println("Attenzione! Stai già utilizzando il Boost!!!");
    }

    private static void notifyHouse(CasaPeer casaPeer) {
        try{
            ArrayList<Integer> listaCaseCodaBoost = new ArrayList<>(casaPeer.getCodaBoost().keySet());
            ArrayList<CasaPeer> listaCase = new ArrayList<>(casaPeer.getListaCase());

            int dimCodaBoost = listaCaseCodaBoost.size();

            for(int i=0; i<dimCodaBoost; i++){
                //prendo l'Id della casa in cosa
                int idBoost = listaCaseCodaBoost.get(i);
                //se la casa corrisponde a tale id, allora prendo tale casa
                for(CasaPeer casa: listaCase){
                    if(casa.getID()==idBoost){
                        contactBoost(casa, casaPeer);
                        casaPeer.removeCodaBoost(idBoost);
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void contactBoost(CasaPeer casa, CasaPeer casaPeer) {
        Thread thBoost = new Thread(()->{
            updateBoost(casa, casaPeer);
        });
        thBoost.start();
        casaPeer.addThreadBoost(thBoost);
    }

    private static void exit(CasaPeer casaPeer, SmartMeterSimulator smart, ServerHome service){
        System.out.println("Esco");

        //se sono in fase di boost notifico le case
        if(casaPeer.isUseBoost()){
            System.out.println("Uscita durante il boost");
            //Fermo lo smart meter
            smart.stopMeGently();
            notifyHouse(casaPeer);
        }

        int i = 0;
        Thread threads [] = new Thread[casaPeer.getListaCase().size()];
        for(CasaPeer casa : casaPeer.getListaCase()){
            if(casa.getID()!=casaPeer.getID()){
                Thread close = new Thread(()->{
                    removeHouse(casa, casaPeer);
                });
                close.start();
                threads[i] = close;
                i++;
            }
        }

        //faccio terminare ai threads il loro lavoro
        for(int k=0; k<casaPeer.getListaCase().size()-1; k++){
            try {
                threads[k].join();
            } catch (InterruptedException e) {
                System.out.println("Richiesta terminazione threads");
            }
        }

        //utile per terminare il thread per le statistiche
        condition = false;

        //chiude gli streams
        for(StreamObserver<MessaggiHouse.SendStatistics> str: casaPeer.getStreams().values()){
            str.onCompleted();
        }

        //chiude i canali
        for(ManagedChannel ch: casaPeer.getChannel().values()){
            ch.shutdown();
        }

        //avviso il server della rimozione
        CasaServer casaServer = new CasaServer(casaPeer.getID(), casaPeer.getIP(),casaPeer.getPorta());
        casaPeer.requestToServer(casaServer, HOST, PORT, "remove");

        //fermo il server dell casa
        service.serverStop();

        System.exit(1);

    }

    private static void Statistics(CustomBuffer buffer, CasaPeer casaPeer) {
        //(Funzionale) passo una classe anonima che implementa Runnable (Lambda expression)
        Thread watcher = new Thread(()->{
            int count = 0;
            boolean first = false;
            int indexProd[] = {0};

            while(condition){
                double sumStat = 0;
                double tot = 0;
                Measurement statProdotta = null;

                try {
                    //toSend contiene media e timestamp
                    Measurement toSend = buffer.get();
                    //Storico di tutte le medie
                    casaPeer.addMediePersonali(toSend);

                    if(casaPeer.getListaCase().size()==1){
                        System.out.println("Consumo singolo: "+ toSend.getValue());
                        count++;
                        first = true;
                    }

                    if(casaPeer.getStatus()=="Coordinator"){
                        Measurement measurement = new Measurement(Integer.toString(casaPeer.getID()), "Local", toSend.getValue(), toSend.getTimestamp());
                        //invio il consumo complessivo al Server
                        sendStatServer(measurement, false);
                    }

                    for(int i = 0; i< casaPeer.getListaCase().size(); i++){
                        if(!(casaPeer.getID()== casaPeer.getListaCase().get(i).getID())){
                            if(first){
                                toSend = AvgStat(casaPeer);
                                casaPeer.addMediaPrimaCasa(toSend);
                                count++;
                                first= false;
                            }
                            sendStatAll(casaPeer.getListaCase().get(i),toSend, casaPeer);
                        }
                    }

                    //quando vi sono + di una casa
                    if(casaPeer.getListaCase().size()>1){
                        ArrayList<Measurement> medieRicevute = new ArrayList<>(casaPeer.getMedie());
                        ArrayList<Measurement> medieToSendServer = new ArrayList<>();

                        for (int j = 0; j < casaPeer.getListaCase().size()-1; j++) {
                            int sizeMedRic = medieRicevute.size()-1;
                            Measurement mTemp = medieRicevute.get(sizeMedRic);
                            medieToSendServer.add(mTemp);
                            medieRicevute.remove(sizeMedRic);
                            System.out.println("Sommo statistica ricevuta: " + mTemp.getValue());
                            sumStat += mTemp.getValue();
                        }


                        //invio la statistica locale al Server
                        if(casaPeer.getStatus()=="Coordinator"){
                            for(int i=0; i<medieToSendServer.size(); i++){
                                sendStatServer(medieToSendServer.get(i), false);
                            }
                        }

                        if(sumStat!=0){
                            if(casaPeer.getmediaPrimaCasa()!=null){
                                statProdotta = casaPeer.getmediaPrimaCasa();
                                casaPeer.resetMediaPrimaCasa();
                            }else{
                                //utile per la prima casa
                                if(count>0){
                                    statProdotta = casaPeer.getMediePersonali().get(count);
                                    count++;
                                }else{
                                    //se non sono la prima casa
                                    if(count==0){
                                        statProdotta = casaPeer.getMediePersonali().get(indexProd[0]);
                                        indexProd[0]++;
                                    }
                                }
                            }

                            System.out.println("Sommo statistica prodotta: " + statProdotta.getValue());
                            tot = statProdotta.getValue() + sumStat;
                            System.out.println("Consumo complessivo: " + tot);
                            System.out.println("------------------------------------");

                            //invio la statistica globale al server
                            if(casaPeer.getStatus()=="Coordinator"){
                                Measurement measurement = new Measurement(Integer.toString(casaPeer.getID()), "Global", tot, statProdotta.getTimestamp());
                                sendStatServer(measurement, true);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        watcher.start();
    }

    private static void sendStatAll(CasaPeer casaPeer, Measurement toSend, CasaPeer thisCasaPeer){
        Thread sendStat = new Thread(()->{
            asynchronousStatistics(casaPeer,toSend, thisCasaPeer);
        });
        sendStat.start();
    }

    private static void election(CasaPeer casaPeer) {
        Thread thElec = new Thread(()->{
            if(casaPeer.getListaCase().size()==1){
                casaPeer.setStatus("Coordinator");
            }else{
                int max = casaPeer.getIDMax();
                if(casaPeer.getID()==max){
                    casaPeer.setStatus("Coordinator");
                }else
                    casaPeer.setStatus("Peer");
            }
        });
        thElec.start();
    }

    private static Measurement AvgStat(CasaPeer casaPeer) {
        double sum=0;
        double average=0;
        long timestamp=0;

        for(int i = 0; i< casaPeer.getMediePersonali().size(); i++){
            sum += casaPeer.getMediePersonali().get(i).getValue();
            if(casaPeer.getMediePersonali().get(i).getTimestamp()>timestamp){
                timestamp = casaPeer.getMediePersonali().get(i).getTimestamp();
            }
        }
        average= sum/ casaPeer.getMediePersonali().size();
        System.out.println("Media delle statistiche: " + average);
        Measurement mTemp = new Measurement(Integer.toString(casaPeer.getID()), null, average, timestamp);
        return mTemp;
    }

    private static void sendStatServer(Measurement statistica, boolean isGlobal){
        ClientConfig clientConfig2 = new DefaultClientConfig();

        clientConfig2.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        Client cliente2 = Client.create(clientConfig2);

        ClientResponse response2;
        WebResource webResource2;

        if(isGlobal){
            TreeMap<Long, Double> statGlobal = new TreeMap<>();
            statGlobal.put(statistica.getTimestamp(), statistica.getValue());

            webResource2 = cliente2
                    .resource("http://"+ HOST+ ":" +  PORT + "/statistiche/addGlobal");
            response2 = webResource2.accept("application/json")
                    .type("application/json").post(ClientResponse.class, statGlobal);
        }else{
            int id = Integer.parseInt(statistica.getId());
            TreeMap<Long, Double> prod = new TreeMap<>();
            prod.put(statistica.getTimestamp(),statistica.getValue());
            TreeMap<Integer, TreeMap<Long, Double>> send = new TreeMap<>();
            send.put(id, prod);

            webResource2 = cliente2
                    .resource("http://"+ HOST+ ":" +  PORT + "/statistiche/addLocal");
            response2 = webResource2.accept("application/json")
                    .type("application/json").post(ClientResponse.class, send);
        }

        if (response2.getStatus()!=200){
            throw new RuntimeException("Failed: HTTP error code: "
                    + response2.getStatus());
        }
    }

    private static void contactServer(CasaPeer casaPeer){
        CasaServer casaServer = new CasaServer(casaPeer.getID(), casaPeer.getIP(), casaPeer.getPorta());
        ClientConfig clientConfig = new DefaultClientConfig();
        //generate JSON of object casa
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        Client cliente = Client.create(clientConfig);

        WebResource webResource = cliente
                .resource("http://"+ HOST+ ":" +  PORT + "/case/add");

        ClientResponse response = webResource.accept("application/json")
                .type("application/json").post(ClientResponse.class, casaServer);

        if(response.getStatus()==401){
            System.out.println("Errore, casa con ID o Porta già presente.");
            System.out.println("Inserire una casa con ID o Porta diversi");
            System.exit(1);
        }

        if (response.getStatus()!=200){
            System.out.println("Errore durante l'inserimento di una casa.");
            System.out.println("Inserire una casa con ID o Porta diversi");
            System.exit(1);
        }else {
            //RESTITUISCO LA LISTA
            WebResource webRes = cliente.resource("http://" + HOST + ":" + PORT + "/case");
            ClientResponse res2 = webRes.accept("application/json").get(ClientResponse.class);

            ArrayList<CasaServer> listaCase
                    = res2.getEntity(new GenericType<ArrayList<CasaServer>>() {
            });

            //aggiungo le case restituite dal server alle case proprie
            for(CasaServer casa: listaCase){
                CasaPeer casaNuova = new CasaPeer(casa.getID(), casa.getIP(), casa.getPorta());
                casaPeer.addCasa(casaNuova);
            }
        }
    }

    private static void presentation(CasaPeer casaPeer, CasaPeer thisCasaPeer){
        Thread streamMessage = new Thread(()->{
            synchronousCall(casaPeer, thisCasaPeer);
        });
        streamMessage.start();
    }

    public static void synchronousCall(CasaPeer casaPeer, CasaPeer thisCasaPeer){
        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+ casaPeer.getPorta()).usePlaintext(true).build();

        thisCasaPeer.addChannel(casaPeer, channel);

        GreetingServiceGrpc.GreetingServiceBlockingStub stub = GreetingServiceGrpc.newBlockingStub(channel);

        MessaggiHouse.HelloRequest request = MessaggiHouse.HelloRequest.newBuilder()
                .setId(thisCasaPeer.getID())
                .setIp(thisCasaPeer.getIP())
                .setPorta(thisCasaPeer.getPorta()).build();

        MessaggiHouse.Response response = stub.message(request);
    }

    public static void removeHouse(CasaPeer casaPeer, CasaPeer thisCasaPeer){
        //mi prendo il canale salvato
        ManagedChannel channel = getChannelHome(casaPeer, thisCasaPeer);

        GreetingServiceGrpc.GreetingServiceBlockingStub stub = GreetingServiceGrpc.newBlockingStub(channel);

        MessaggiHouse.RemoveCasa request = MessaggiHouse.RemoveCasa.newBuilder()
                .setId(thisCasaPeer.getID())
                .setMessage("exit")
                .build();

        MessaggiHouse.Response response = stub.exit(request);
    }

    public static void asynchronousStatistics(CasaPeer casaPeer, Measurement measurement, CasaPeer thisCasaPeer){
        //mi prendo il canale salvato
        ManagedChannel channel = getChannelHome(casaPeer, thisCasaPeer);

        GreetingServiceGrpc.GreetingServiceStub stub2 = GreetingServiceGrpc.newStub(channel);

        MessaggiHouse.SendStatistics stat = MessaggiHouse.SendStatistics.newBuilder()
                .setId(thisCasaPeer.getID())
                .setMedia(measurement.getValue())
                .setTimestamp(measurement.getTimestamp())
                .build();

        StreamObserver<MessaggiHouse.SendStatistics> streamStatistiche = stub2.streamStatistics(new StreamObserver<MessaggiHouse.Response>() {
            @Override
            public void onNext(MessaggiHouse.Response value) {
                //System.out.println("Risposta: " +  value);
            }

            @Override
            public void onError(Throwable t) {
                //se una casa non è più raggiungibile, allora la rimuovo
                thisCasaPeer.removeCasa(casaPeer.getID());

                //verifica se la casa eliminata era il coordinatore
                if(casaPeer.getID()>thisCasaPeer.getID()){
                    int max = thisCasaPeer.getIDMax();
                    if(thisCasaPeer.getID()==max){
                        thisCasaPeer.setStatus("Coordinator");
                    }
                }
                //se si...
                if(thisCasaPeer.getStatus()=="Coordinator"){
                    //avviso al server dell'uscita di tale casa
                    CasaServer casaServer = new CasaServer(casaPeer.getID(), casaPeer.getIP(),casaPeer.getPorta());
                    thisCasaPeer.requestToServer(casaServer, HOST, PORT, "remove");
                }
            }

            @Override
            public void onCompleted() {
            }
        });

        streamStatistiche.onNext(stat);

        thisCasaPeer.addStreams(casaPeer, streamStatistiche);
    }

    private static void sendBoostRequest(CasaPeer casaPeer, CasaPeer thisCasaPeer, long timestamp) {
        Thread sendRequestBoost = new Thread(()->{
            sendBoost(casaPeer, thisCasaPeer, timestamp);
        });
        sendRequestBoost.start();
        casaPeer.addThreadBoost(sendRequestBoost);
    }

    public static void sendBoost(CasaPeer casaPeer, CasaPeer thisCasaPeer, long timestamp){
        ManagedChannel channel = getChannelHome(casaPeer, thisCasaPeer);

        GreetingServiceGrpc.GreetingServiceBlockingStub stub = GreetingServiceGrpc.newBlockingStub(channel);

        MessaggiHouse.RequestBoost request = MessaggiHouse.RequestBoost.newBuilder()
                .setResource("Boost")
                .setId(thisCasaPeer.getID())
                .setTimestamp(timestamp)
                .build();

        MessaggiHouse.ResponseBoost response = stub.boost(request);

        if(response.getStatus().equals("OK")){
            thisCasaPeer.setConsensi();
        }
    }

    private static ManagedChannel getChannelHome(CasaPeer casaPeer, CasaPeer thisCasaPeer) {
        //prendo il canale della casa
        HashMap<CasaPeer, ManagedChannel> channels = new HashMap<>(thisCasaPeer.getChannel());

        final ManagedChannel channel;

        if(channels.containsKey(casaPeer))
            channel= channels.get(casaPeer);
        else{
            channel = ManagedChannelBuilder.forTarget("localhost:"+ casaPeer.getPorta()).usePlaintext(true).build();
            thisCasaPeer.addChannel(casaPeer, channel);
        }
        return channel;
    }

    public static void updateBoost(CasaPeer casaPeer, CasaPeer thisCasaPeer){
        ManagedChannel channel = getChannelHome(casaPeer, thisCasaPeer);

        GreetingServiceGrpc.GreetingServiceBlockingStub stub = GreetingServiceGrpc.newBlockingStub(channel);

        MessaggiHouse.UpdateBoost request = MessaggiHouse.UpdateBoost.newBuilder()
                .setMessage("OK")
                .build();

        MessaggiHouse.ResponseBoost response = stub.updateBoost(request);
    }
}


