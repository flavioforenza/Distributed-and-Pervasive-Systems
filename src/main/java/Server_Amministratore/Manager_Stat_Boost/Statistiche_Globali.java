package Server_Amministratore.Manager_Stat_Boost;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Statistiche_Globali {

    @XmlElement(name="statGlobali")
    private Map<Long, Double> statGlobali;

    public static Statistiche_Globali instance;

    public Statistiche_Globali(){
        statGlobali = new TreeMap<>();
    }

    public synchronized static Statistiche_Globali getInstance(){
        if(instance==null)
            instance = new Statistiche_Globali();
        return instance;
    }

    public synchronized void addStatGlobal(TreeMap<Long, Double> info){
        Map.Entry<Long, Double> entry = info.entrySet().iterator().next();
        double value = entry.getValue();
        long timestamp = entry.getKey();
        statGlobali.put(timestamp, value);

        System.out.println("Statistica globale ricevuta: " + value + " Timestamp: " + timestamp);
    }

    public synchronized TreeMap<Long, Double> getStatGlobal(int n){
        TreeMap<Long,Double> statistiche = new TreeMap<>(Collections.reverseOrder());
        statistiche.putAll(statGlobali);

        TreeMap<Long,Double> send = new TreeMap<>();

        if(n==0){
            send.putAll(statistiche);
        }else{
            if(statistiche.size()>=n){
                for(int i=n; i>0; i--){
                    long timestamp = (long) statistiche.keySet().toArray()[i];
                    double value = statistiche.get(timestamp);
                    send.put(timestamp, value);
                }
            }
        }
        return send;
    }
}
