package Server_Amministratore;

import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;

public class Server {

    private static final String HOST = "localhost";
    private static final int PORT = 1339;


    public static void main(String[] args) throws IOException {

        HttpServer server = HttpServerFactory.create("http://"+HOST+":"+PORT+"/");
        server.start();

        System.out.println("Server Amministratore running!");
        System.out.println("Server Amministratore  started on: http://"+HOST+":"+PORT);

        System.out.println("Hit return to stop...");
        System.in.read();
        System.out.println("Stopping server");
        server.stop(0);
        System.out.println("Server_Amministratore  stopped");
    }
}
