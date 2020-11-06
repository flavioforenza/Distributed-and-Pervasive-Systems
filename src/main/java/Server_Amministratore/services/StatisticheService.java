package Server_Amministratore.services;

import Server_Amministratore.Manager_Stat_Boost.Statistiche_Globali;
import Server_Amministratore.Manager_Stat_Boost.Statistiche_Locali;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.TreeMap;

@Path("statistiche")
public class StatisticheService {

    @Path("addGlobal")
    @POST
    @Consumes("application/json")
    public Response addStatGlob(TreeMap<Long, Double> mm){
        Statistiche_Globali.getInstance().addStatGlobal(mm);
        return Response.ok().build();
    }

    @Path("addLocal")
    @POST
    @Consumes("application/json")
    public Response addStatLocal(TreeMap<Integer, TreeMap<Long, Double>> info){
        Statistiche_Locali.getInstance().addStatLocal(info);
        return Response.ok().build();
    }

    @Path("getLocal/{ID}/{n}/")
    @GET
    @Produces("application/json")
    public Response getLocal(@PathParam("ID") int ID, @PathParam("n") int n){
        return Response.ok(Statistiche_Locali.getInstance().getStatLocal(ID, n)).build();
    }

    @Path("getGlobal/{n}/")
    @GET
    @Produces("application/json")
    public Response getGlobal(@PathParam("n") int n){
        return Response.ok(Statistiche_Globali.getInstance().getStatGlobal(n)).build();
    }
}
