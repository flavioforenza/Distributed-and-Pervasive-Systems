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
public class Statistiche_Locali {

    @XmlElement(name="statLocali")
    private Map<Integer, TreeMap<Long, Double>> statLocali;

    private static Statistiche_Locali instance;

    public Statistiche_Locali(){
        statLocali = new TreeMap<>();
    }

    public synchronized static Statistiche_Locali getInstance(){
        if(instance==null)
            instance = new Statistiche_Locali();
        return instance;
    }

    public synchronized void addStatLocal(TreeMap<Integer, TreeMap<Long, Double>> info){
        int id = info.firstKey();
        long timestamp = info.get(id).firstKey();
        double value = info.get(id).get(timestamp);

        if(!statLocali.containsKey(id)){
            statLocali.putAll(info);
        }else{
            statLocali.get(id).put(timestamp, value);
        }

        System.out.println("Statistica locale: " + value + " Timestamp: " + timestamp + " Casa: " + id);

    }

    public synchronized TreeMap<Long, Double> getStatLocal(int id, int n){
        //prendo tutti i valori di una casa
        TreeMap<Long, Double> valori = new TreeMap<>(Collections.reverseOrder());
        valori.putAll(statLocali.get(id));

        TreeMap<Long, Double> statLocal = new TreeMap<>();

        if(n==0){
            statLocal.putAll(valori);
        }else{
            if(valori.size()>=n){
                for(int i = n; i>0; i--){
                    //prendo gli ultimi n timestamp
                    long timestamp = (long) valori.keySet().toArray()[i];
                    double value = valori.get(timestamp);
                    statLocal.put(timestamp,value);
                }
            }
        }

        return statLocal;
    }
}
