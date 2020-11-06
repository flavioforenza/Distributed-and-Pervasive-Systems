package Amministratore;

import Admin_Server.MessaggiAdminServer;
import Server_Amministratore.Manager_Admin.AdminServer;
import Server_Amministratore.Manager_Admin.Admins;
import Server_Amministratore.Manager_Case.CasaServer;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import io.grpc.stub.StreamObserver;


import java.util.*;

public class Client_Start {

    private static boolean status = true;

    static final String HOST = "localhost";
    static final int PORT = 1339;
    static Server_Client server;
    private static Client_Amministratore admin;

    public static void main(String[] args) {
        String ip = args[0];
        int port = Integer.parseInt(args[1]);

        //mi faccio riconocere dal server
        admin = new Client_Amministratore(ip, port);

        //mi faccio aggiungere dal server
        contactServer(admin);

        //Avvio il server
        server = new Server_Client(admin);

        //menu
        start();

        server.ServerRun(admin);
    }

    private static void start(){
        Thread administrator = new Thread(()->{
            while(status){
                choose(Thread.currentThread());
            }
        });
        administrator.start();
    }

    private static void contactServer(Client_Amministratore admin) {
        AdminServer adminServer = new AdminServer(admin.getIp(), admin.getPort());
        ClientResponse response = serviceServer("/add", adminServer, false);

        if(response.getStatus()==401){
            System.out.println("Errore, esiste già un Admin con questa porta.");
            System.out.println("Inserire un Admin con una porta diversa.");
            System.exit(1);
        }

        if (response.getStatus()!=200){
            System.out.println("Errore durante l'inserimento di un Client");
            throw new RuntimeException("Failed: HTTP error code: "
                    + response.getStatus());
        }
    }

    private static void quit(Thread thread){
        cls();

        AdminServer adminSever = new AdminServer(admin.getIp(), admin.getPort());

        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        Client cliente = Client.create(clientConfig);

        WebResource webRes = cliente.resource("http://" + HOST + ":" + PORT + "/admins/remove");
        ClientResponse response = webRes.accept("application/json")
                .type("application/json").delete(ClientResponse.class, adminSever);

        if (response.getStatus()!=200){
            System.out.println("Errore durante la cancellazione di un Client");
            throw new RuntimeException("Failed: HTTP error code: "
                    + response.getStatus());
        }

        System.out.println("Disconnect");
        thread.interrupt();
        status = false;

        //chiudo tutti i canali
        ArrayList<StreamObserver<MessaggiAdminServer.receiveNotification>> streams = new ArrayList<>(admin.getStream());
        for(int i=0; i<streams.size(); i++){
            streams.get(i).onCompleted();
        }

        server.serverStop();
        System.exit(1);

    }

    private static ClientResponse serviceServer(String request, AdminServer client, boolean stat){

        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        Client cliente = Client.create(clientConfig);

        ClientResponse response = null;
        WebResource webRes;

        //richiesta esisteza casa + lista case
        if(client == null && stat == false){
            webRes = cliente.resource("http://" + HOST + ":" + PORT + "/case" + request);
            response = webRes.accept("application/json").get(ClientResponse.class);
        }

        //richiesta statistiche locali o globali
        if(client==null && stat == true){
            //usato per richiedere statistiche e case
            webRes = cliente.resource("http://" + HOST + ":" + PORT + "/statistiche" + request);
            response = webRes.accept("application/json").get(ClientResponse.class);
        }

        //richiesta di aggiunta Admin alla lista del server
        if(client!=null && stat==false){
            //usato per farsi aggiungere come Admin alla lista del server amministratore
            webRes = cliente.resource("http://" + HOST + ":" + PORT + "/admins" + request);
            response = webRes.accept("application/json")
                    .type("application/json").post(ClientResponse.class, client);
        }

        return response;
    }

    private static void choose(Thread thread) {
        cls();
        System.out.println("************************ SELEZIONA UN COMANDO ************************");
        System.out.println("a) Lista delle case presenti nella rete");
        System.out.println("b) Statistiche locali di una casa");
        System.out.println("c) Statistiche globali condominiali");
        System.out.println("d) Deviazione stadard + Media delle statistiche locali di una casa");
        System.out.println("e) Deviazione stadard + Media delle statistiche globali del condominio");
        System.out.println("q) Uscita");
        System.out.println("**********************************************************************");

        String choice = null;
        Scanner scanner = new Scanner(System.in);

        do{
            choice = scanner.nextLine();
            switch (choice){
                case "a":
                    getListHome();
                    break;

                case "b":
                    statLoc();
                    break;

                case "c":
                    statGlob();
                    break;

                case "d":
                    calcSingle();
                    break;

                case "e":
                    calcMulti();
                    break;

                case "q":
                    quit(thread);
                    break;

                default:
                    System.out.println("Scelta errata, selezionare un nuovo comando.");
            }
        }while(!choice.equals("q") && status);
    }

    private static void calcMulti(){
        TreeMap<Long, Double> statistics = new TreeMap<>();
        statistics.putAll(getStatGlobal());
        if(statistics.size()>0){
            compute(statistics);
        }else{
            System.out.println("Ancora nessuna statistica prodotta");
            exit();
        }
    }

    private static void compute(TreeMap<Long, Double> statistics){
        double average =0;
        double sum =0;
        double std =0;
        for(Long ll: statistics.keySet()){
            sum += statistics.get(ll);
        }

        average = sum/statistics.size();

        System.out.println("Media delle statistiche: " + average);

        for(Long ll: statistics.keySet()){
            std += Math.pow(statistics.get(ll)-average, 2);
        }

        System.out.println("Deviazione Standard statistiche: " + Math.sqrt(std/statistics.size()));
        exit();
    }

    private static void calcSingle() {
        TreeMap<Long, Double> statistics = new TreeMap<>();
        statistics.putAll(getStatLocal());

        if(statistics.size()>0){
            compute(statistics);
        }else{
            System.out.println("Ancora nessuna statistica prodotta");
            exit();
        }
    }

    private static int controlInsert(String element){
        Scanner scan = new Scanner(System.in);
        int number = 0;
        do{
            //se non è un numero positivo
            System.out.println("Inserisci un " + element + " (positivo):");
            while(!scan.hasNextInt()){
                System.out.println("Inserimento Errato.");
                System.out.println("Inserisci un " + element + " (positivo):");
                scan.next();
            }
            number = scan.nextInt();
        }while(number<0);
        //numero corretto
        return number;
    }

    //restituisce le statistiche di una sola casa
    private static TreeMap<Long, Double> getStatLocal() {
        cls();
        int id =0;
        int n =0;

        ClientResponse response = null;
        do{
            System.out.println("Richiesta statistiche 'Locali'");
            id = controlInsert("ID");
            //controllo se la casa esiste
            String request = "/get/" + id;
            response = serviceServer(request, null, false);

            if (response.getStatus()!=200){
                System.out.println("Errore, non esiste una casa con questo ID");
                exit();
            }

        }while(response.getStatus() != 200);

        //l'id esiste fin qui
        ClientResponse secondResponse=null;
        do{
            System.out.println("Inserisci '0' per visualizzare tutte le statistiche locali prodotte:");
            System.out.print("Oppure, ");
            n = controlInsert("Numero di statistiche Locali da prelevare");
            String secondRequest = "/getLocal/" + id + "/" + n + "/";
            secondResponse = serviceServer(secondRequest, null, true);

        }while(secondResponse.getStatus()!=200);

        //mi faccio ritornare le statistiche
        TreeMap<Long, Double> statistics = secondResponse.getEntity(new GenericType<TreeMap<Long, Double>>(){});

        return statistics;
    }

    private static TreeMap<Long, Double> getStatGlobal(){
        cls();
        int n =0;
        TreeMap<Long, Double> statistiche = new TreeMap<>();
        ClientResponse response = null;
        do{
            System.out.println("Richiesta statistiche 'Globali'");
            System.out.println("Inserisci '0' per visualizzare tutte le statistiche Globali prodotte:");
            System.out.print("Oppure, ");
            n = controlInsert("Numero di statistiche Globali da prelevare");

            String request = "/getGlobal/" + n + "/";

            response = serviceServer(request,null, true);

        }while(response.getStatus()!=200);
        statistiche.putAll(response.getEntity(new GenericType<TreeMap<Long, Double>>(){}));

        return  statistiche;
    }

    private static void statGlob() {
        cls();

        TreeMap<Long, Double> statistiche = new TreeMap<>();
        statistiche.putAll(getStatGlobal());

        if(statistiche.size()==0){
            System.out.println("Numero di statistiche 'Globali' prodotte insufficienti");
        }else{
            for(Long ll: statistiche.keySet()){
                System.out.println("Statistica Globale: " + statistiche.get(ll) + " Timestamp: " + ll);
            }
        }

        exit();
    }

    //stampa le statistiche di una sola casa
    private static void statLoc() {
        TreeMap<Long, Double> statistics = new TreeMap<>();
        statistics.putAll(getStatLocal());

        if(statistics.size()==0){
            System.out.println("Numero di statistiche 'Locali' prodotte insufficienti");
        }else{
            for (Long ll : statistics.keySet()) {
                System.out.println("Statistica locale: " + statistics.get(ll) + " Timestamp: " + ll);
            }
        }

        exit();
    }

    private static void getListHome() {
        cls();
        ClientResponse response = serviceServer("",null, false);

        ArrayList<CasaServer> listaCase
                = response.getEntity(new GenericType<ArrayList<CasaServer>>() {
        });

        if(listaCase.size()==0){
            System.out.println("Nessuna casa presente.");
        }else{
            System.out.println("Numero di case presenti: " + listaCase.size());
            System.out.println("Case presenti:\n");
            for(CasaServer casa: listaCase)
                System.out.println("ID:" +casa.getID() + " IP:" + casa.getIP() + " Porta:" + casa.getPorta());
        }

        exit();
    }

    private static void exit(){
        String choiche = null;
        do{
            System.out.println("Premi 'q' per ritornare al Menu");
            Scanner scanner = new Scanner(System.in);
            choiche = scanner.nextLine();
            switch (choiche){
                case "q":
                    choose(Thread.currentThread());
                    break;

                default:
                    System.out.println("Scelta errata");
            }
        }while(!choiche.equals("q"));
    }

    private static void cls() {
        System.out.print("\033[H\033[2J");
    }

}
