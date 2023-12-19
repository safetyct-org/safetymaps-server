package nl.opengeogroep.safetymaps;

import java.io.IOException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class IOExceptionMapper implements ExceptionMapper<IOException> {
    @Override
    public Response toResponse(IOException exception) {
        String exceptionSimpleName = exception.getCause().getClass().getSimpleName();

        if ("ClientAbortException".equals(exceptionSimpleName)) {
          return null;
        }

        // return ResponseGen.internalErrorJSON(ServiceResponse.create("Internal", "internal", errorMessage, httpStatusCode));
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exception.getMessage()).type((MediaType.TEXT_PLAIN)).build();
    }
}
