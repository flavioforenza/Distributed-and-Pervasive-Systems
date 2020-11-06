package Server_Amministratore.services;


import Server_Amministratore.Manager_Case.CasaServer;
import Server_Amministratore.Manager_Case.Case;
import Server_Amministratore.Manager_Stat_Boost.Boost;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("case")
public class CaseService {

    //restituisce la lista delle case
    @GET
    @Produces("application/json")
    //viene restituito il singleton
    public Response getCaseList(){
        return Response.ok(Case.getInstance().getCaseList()).build();
    }

    //permette di inserire una casa (ID, IP e porta)
    @Path("add")
    @POST
    @Consumes("application/json")
    public Response addCasa(CasaServer u){
        boolean exists = Case.getInstance().add(u);
        if(!exists){
            return Response.ok().build();
        }else{
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    //rimuove una casaServer che decide di togliersi dalla rete
    @Path("remove/{ID}")
    @DELETE
    @Consumes("application/json")
    public Response removeCasa(@PathParam("ID") int ID){
        Case.getInstance().removeCasa(ID);
        return Response.ok().build();
    }

    //permette di controllare l'esistenza di una casa
    @Path("get/{ID}")
    @GET
    @Produces("application/json")
    public Response controlExistID(@PathParam("ID") int ID){
        boolean search = Case.getInstance().controlID(ID);
        if(search)
            return Response.ok().build();
        else
            return Response.status(Response.Status.NOT_FOUND).build();

    }

    @Path("addRequestBoost/{ID}")
    @POST
    @Consumes("application/json")
    public Response addRequestBoost(@PathParam("ID") int ID){
        Boost.getInstance().addRequestBoost(ID);
        return Response.ok().build();
    }
}
