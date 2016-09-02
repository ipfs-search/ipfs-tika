package com.ipfssearch.ipfstika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import org.ipfs.api.IPFS;
import org.ipfs.api.Multihash;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.Link;
import org.apache.tika.exception.TikaException;

import org.xml.sax.SAXException;

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
        String uri = session.getUri();

        try {
            return newFixedLengthResponse(getResponse(uri));
        } catch (IOException ioe) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, ioe.getMessage());
        }
    }

    private String getResponse(String uri) throws IOException {
        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");

        String hash = uri.substring(1);
        Multihash filePointer = Multihash.fromBase58(hash);

        InputStream inputStream;
        String output;

        inputStream = ipfs.catStream(filePointer);

        AutoDetectParser parser = new AutoDetectParser();
        LinkContentHandler link_handler = new LinkContentHandler();
        BodyContentHandler body_handler = new BodyContentHandler();
        LanguageHandler language_handler = new LanguageHandler();
        TeeContentHandler handler = new TeeContentHandler(link_handler, body_handler, language_handler);
        Metadata metadata = new Metadata();


        try {
            parser.parse(inputStream, handler, metadata);
            output = metadata.toString();
        } catch (TikaException e) {
            throw new IOException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        } finally {
            inputStream.close();
        }

        return output;
    }
}
