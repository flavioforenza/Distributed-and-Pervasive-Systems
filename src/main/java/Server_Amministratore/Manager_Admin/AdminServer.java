package Server_Amministratore.Manager_Admin;

public class AdminServer {

    private String ipAdmin;
    private int portAdmin;

    private AdminServer(){}

    public AdminServer(String ipAdmin, int portAdmin) {
        this.ipAdmin = ipAdmin;
        this.portAdmin = portAdmin;
    }

    public String getIpAdmin() {
        return ipAdmin;
    }

    public int getPortAdmin() {
        return portAdmin;
    }
}
