package Server_Amministratore.Manager_Case;

public class CasaServer {

    private int ID;
    private String IP;
    private int porta;

    private CasaServer(){}

    public CasaServer(int ID, String IP, int porta) {
        this.ID = ID;
        this.IP = IP;
        this.porta = porta;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public String getIP() {
        return IP;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    public int getPorta() {
        return porta;
    }

    public void setPorta(int porta) {
        this.porta = porta;
    }

}
