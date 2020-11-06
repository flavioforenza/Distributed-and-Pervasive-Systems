package Amministratore;

import Admin_Server.AdminServerGrpc;
import Admin_Server.MessaggiAdminServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

public class Server_Client extends AdminServerGrpc.AdminServerImplBase {

    private Client_Amministratore client;
    private Server server;

    public Server_Client(Client_Amministratore cliente){
        this.client=cliente;
    }

    public void ServerRun(Client_Amministratore client){
        try{
            server = ServerBuilder.forPort(client.getPort()).addService(new Server_Client(client)).build();
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
    public StreamObserver<MessaggiAdminServer.sendNotification> streamNotify(StreamObserver<MessaggiAdminServer.receiveNotification> responseObserver) {

        client.addStream(responseObserver);

        return new StreamObserver<MessaggiAdminServer.sendNotification>() {
            @Override
            public void onNext(MessaggiAdminServer.sendNotification request) {
                System.out.println("Notifica: " + request.getNotification());

                MessaggiAdminServer.receiveNotification response =
                        MessaggiAdminServer.receiveNotification.newBuilder().setResponseNotification("Ok from Admin").build();

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
}
