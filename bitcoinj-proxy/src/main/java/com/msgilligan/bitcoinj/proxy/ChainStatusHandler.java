package com.msgilligan.bitcoinj.proxy;

import com.msgilligan.bitcoinj.rpc.JsonRpcRequest;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.client.HttpClient;

import java.net.URISyntaxException;

/**
 * Handle a GET request by posting a "getblockchaininfo" and returning the response
 */
public class ChainStatusHandler extends AbstractJsonRpcHandler implements Handler {

    public ChainStatusHandler() throws URISyntaxException {
        super();
    }

    @Override
    public void handle(Context ctx) throws Exception {
        ctx.get(HttpClient.class).requestStream(remoteURI, requestSpec -> {
            requestSpec.post();
            requestSpec.body(body ->
                    body.type(jsonType).text(buildStatusReq()));
            requestSpec.redirects(0);
            if (remoteUserName != null) {
                requestSpec.basicAuth(remoteUserName, remotePassword);
            }
        }).then(responseStream -> {
            // TODO: Extract from JsonRpcResponse and return more restful JSON format (no RPC wrapper)
            responseStream.forwardTo(ctx.getResponse());
        });
    }

    private String buildStatusReq() {
        JsonRpcRequest req = new JsonRpcRequest("getblockchaininfo");
        return requestToString(req);
    }
}
