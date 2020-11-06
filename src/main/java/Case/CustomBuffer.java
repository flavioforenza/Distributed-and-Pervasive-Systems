package Case;

import Smart_Meter.Buffer;
import Smart_Meter.Measurement;

import java.util.LinkedList;

public class CustomBuffer implements Buffer {
    //Buffer che conterrà tutte le rilevazioni
    private LinkedList<Measurement> buffer = new LinkedList<>();
    //lista che conterrà tutte le medie
    private LinkedList<Measurement> medie = new LinkedList<>();

    public CustomBuffer() {
        this.buffer = new LinkedList<>();
    }

    //Bloccante
    public Measurement get() throws InterruptedException {
        //accesso sincronizzato alle medie
        synchronized (medie){
            if(medie.isEmpty()){
                //bloccante
                medie.wait();
            }
            //restituisco
            return medie.removeFirst();
        }
    }

    @Override
    public void addMeasurement(Measurement m) {
        synchronized (buffer){
            //aggiungo una misura (id, type, value, timestamp)
            this.buffer.addLast(m);
            //sliding windows se buffer ha dimensione 24
            if(this.buffer.size()==24){
                slidingWindows();
            }
        }
    }

    private void slidingWindows() {
        Measurement mNew = null;
        mNew = avg();
        //aggiungo la media calcolata alla lista delle medie
        synchronized (medie) {
            medie.addFirst(mNew);
            //notifico i threads
            medie.notifyAll();
        }
    }

    private Measurement avg(){
        double sum = 0;
        double avg = 0;
        long timeStamp=0;

        //effettuo la media delle prime 24 misurazioni
        for (int i = 0; i < 24; i++) {
            sum += buffer.get(i).getValue();
            //prendo l'ultimo timestamp
            if(i==23)
                timeStamp = buffer.get(i).getTimestamp();
        }
        avg = sum/24;
        //creo un oggetto dove poter inserire media e timestamp
        Measurement measurement = new Measurement(buffer.getFirst().getId(),
                buffer.getFirst().getType(),avg, timeStamp);

        //rimuovo le prime 12 misurazioni (2 PASSATA)
        for(int i=0; i<12; i++) {
            buffer.removeFirst();
        }

        return measurement;
    }
}
