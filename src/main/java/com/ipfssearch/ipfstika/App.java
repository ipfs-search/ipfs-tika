package com.ipfssearch.ipfstika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import org.ipfs.api.IPFS;
import org.ipfs.api.Multihash;

public class App extends NanoHTTPD {

    public App() throws IOException {
        super(8081);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nRunning! Point your browsers to http://localhost:8081/ \n");
    }

    public static void main(String[] args) {
        try {
            new App();
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");

        String uri = session.getUri();
        String hash = uri.substring(1);
        Multihash filePointer = Multihash.fromBase58(hash);

        try {
            InputStream inputStream = ipfs.catStream(filePointer);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, e.getMessage());
        }

        return newFixedLengthResponse("bla");
    }
}
