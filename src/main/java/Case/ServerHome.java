package Case;

import Smart_Meter.Measurement;
import Smart_Meter.SmartMeterSimulator;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import subpackage.MessaggiHouse.*;
import subpackage.GreetingServiceGrpc.GreetingServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


public class ServerHome extends GreetingServiceImplBase {

    private CasaPeer casaPeer;
    private Server server;

    public ServerHome(CasaPeer casaPeer) {
        this.casaPeer = casaPeer;
    }

    public void ServerRun(CasaPeer casaPeer){
        try{
            server = ServerBuilder.forPort(casaPeer.getPorta()).addService(new ServerHome(casaPeer)).build();
            //Server Home start
            server.start();

            server.awaitTermination();

        }catch (IOException e) {

            e.printStackTrace();

        } catch (InterruptedException e) {

            e.printStackTrace();

        }
    }

    public void serverStop(){
        System.out.println("Chiudo il server");
        server.shutdownNow();
    }

    @Override
    public StreamObserver<SendStatistics> streamStatistics(StreamObserver<Response> responseObserver) {
        return new StreamObserver<SendStatistics>() {
            @Override
            public void onNext(SendStatistics value) {
                Measurement input = new Measurement(Integer.toString(value.getId()),
                        null,value.getMedia(),value.getTimestamp());

                casaPeer.addMedie(input);

                Response response = Response.newBuilder()
                        .setStatus("OK")
                        .build();
                responseObserver.onNext(response);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void message(HelloRequest request, StreamObserver<Response> responseObserver) {
        //la richiesta è di tipo HelloRequest (definito in .proto)
        //System.out.println("Richiesta "+ request);

        casaPeer.addCasa(new CasaPeer(request.getId(), request.getIp(), request.getPorta()));

        if(request.getId()>casaPeer.getID())
            casaPeer.setStatus("Peer");

        //costruisco la richiesta di tipo HelloResponse (sempre definito in .proto)
        Response resp = Response.newBuilder()
                .setStatus("OK").build();

        //passo la risposta nello stream
        responseObserver.onNext(resp);

        //completo e finisco la comunicazione
        responseObserver.onCompleted();
    }

    @Override
    public void exit(RemoveCasa request, StreamObserver<Response> responseObserver) {
        if(request.getMessage().equals("exit")){
            casaPeer.removeCasa(request.getId());
            System.out.println("Casa rimossa ID:" + request.getId());

            //chiudo e rimuovo lo stream + canale della casa che ha richiesto di uscire
            ArrayList<CasaPeer> listaHouseCanali = new ArrayList<>(casaPeer.getStreams().keySet());
            HashMap<CasaPeer, StreamObserver<SendStatistics>> streams = new HashMap<>(casaPeer.getStreams());
            HashMap<CasaPeer, ManagedChannel> channels = new HashMap<>(casaPeer.getChannel());

            for(int i =0; i<listaHouseCanali.size(); i++){
                if(listaHouseCanali.get(i).getID()==request.getId()){
                    streams.get(listaHouseCanali.get(i)).onCompleted();
                    casaPeer.removeStream(request.getId());
                    channels.get(listaHouseCanali.get(i)).shutdown();
                    casaPeer.removeChannel(listaHouseCanali.get(i));
                }
            }

            //controllo coordinatore
            Thread thElec = new Thread(()->{
                if(casaPeer.getListaCase().size()==1){
                    casaPeer.setStatus("Coordinator");
                }else{
                    if(casaPeer.getID()<request.getId()){
                        int max = casaPeer.getIDMax();
                        if(casaPeer.getID()==max){
                            System.out.println("Sono il coordinatore");
                            casaPeer.setStatus("Coordinator");
                        }
                    }
                }
            });
            thElec.start();
        }

        Response response = Response.newBuilder().setStatus("OK").build();

        //passo la risposta nello stream
        responseObserver.onNext(response);

        //completo e finisco la comunicazione
        responseObserver.onCompleted();
    }

    @Override
    public void boost(RequestBoost request, StreamObserver<ResponseBoost> responseObserver) {
        //System.out.println("Richiesta boost:" + request);
        ResponseBoost response = null;
        boolean timestamp = false;
        boolean id = false;

        if(!casaPeer.isRequestBoost() && !casaPeer.isUseBoost()){
            System.out.println("Richiesta Boost casa ID: " + request.getId());
            response = ResponseBoost.newBuilder()
                    .setId(casaPeer.getID())
                    .setStatus("OK")
                    .build();
        }

        if(casaPeer.isRequestBoost() && casaPeer.isUseBoost()){
            System.out.println("Richiesta boost casa ID: " + request.getId());
            System.out.println("Accodo richiesta boost casa ID: " + request.getId());
            casaPeer.addCodaBoost(request.getId(), responseObserver);

            response = ResponseBoost.newBuilder()
                    .setId(casaPeer.getID())
                    .setStatus("").build();
        }

        if(casaPeer.isRequestBoost() && !casaPeer.isUseBoost()){
            System.out.println("Richiesta boost casa ID: " + request.getId());
            //controllo del timestamp
            if(request.getTimestamp()<casaPeer.getLastTimestamp()){
                response = ResponseBoost.newBuilder()
                        .setId(casaPeer.getID())
                        .setStatus("OK").build();
                timestamp=true;
            }
            //se il timestamp è uguale, controllo l'ID
            if(request.getTimestamp()==casaPeer.getLastTimestamp()){
                if(request.getId()<casaPeer.getID()){
                    response = ResponseBoost.newBuilder()
                            .setId(casaPeer.getID())
                            .setStatus("OK").build();
                }
                id = true;
            }

            if(!timestamp && !id){
                //se ho il timestamp minore di chi mi ha fatto richiesta
                System.out.println("Accodo richiesta boost casa ID: " + request.getId());
                casaPeer.addCodaBoost(request.getId(), responseObserver);

                response = ResponseBoost.newBuilder()
                        .setId(casaPeer.getID())
                        .setStatus("").build();
            }
        }

        //passo la risposta nello stream
        responseObserver.onNext(response);

        //completo e finisco la comunicazione
        responseObserver.onCompleted();
    }

    @Override
    public void updateBoost(UpdateBoost request, StreamObserver<ResponseBoost> responseObserver) {

        Thread thB = new Thread(()->{
            if(request.getMessage().equals("OK")){
                //System.out.println("Ho ricevuto Ok dalla casa dopo");
                casaPeer.setConsensi();
            }
        });
        thB.start();
        casaPeer.addThreadBoost(thB);

        ResponseBoost response = ResponseBoost.newBuilder()
                .setId(casaPeer.getID())
                .setStatus("OK ho ricevuto il consenso")
                .build();

        //passo la risposta nello stream
        responseObserver.onNext(response);

        //completo e finisco la comunicazione
        responseObserver.onCompleted();
    }
}