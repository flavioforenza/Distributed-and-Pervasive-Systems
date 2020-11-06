package Server_Amministratore.services;


import Server_Amministratore.Manager_Admin.AdminServer;
import Server_Amministratore.Manager_Admin.Admins;
import subpackage.MessaggiHouse;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("admins")
public class AdminService {

    @Path("add")
    @POST
    @Consumes("application/json")
    public Response addAdmin(AdminServer adminServer){
        boolean result = Admins.getInstance().addAdmin(adminServer);
        if(!result){
            return Response.ok().build();
        }else
            return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    @Path("remove")
    @DELETE
    @Consumes("application/json")
    public Response removeAdmin(AdminServer adminServer){
        Admins.getInstance().removeAdmin(adminServer);
        return Response.ok().build();
    }
}
