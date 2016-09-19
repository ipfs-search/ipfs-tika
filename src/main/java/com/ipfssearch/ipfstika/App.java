package com.ipfssearch.ipfstika;

import java.io.IOException;
import java.io.InputStream;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import fi.iki.elonen.NanoHTTPD;

import org.apache.commons.lang.StringUtils;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.Link;
import org.apache.tika.exception.TikaException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
            System.out.println(uri);
            return newFixedLengthResponse(getResponse(uri));
        } catch (IOException ioe) {
            System.err.println("Internal server error:\n" + ioe.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, ioe.getMessage());
        }
    }

    private List<String> getAbsoluteLinks(String parentURL, List<Link> links) {
        List<String> links_out = new ArrayList<String>();
        String uri;

        URL url_;
        try {
            url_ = new URL(parentURL);
        } catch (MalformedURLException e) {
            System.err.println("MalformedURLException:\n" + e.getMessage());
            return links_out;
        }

        for (Link link : links) {
            uri = link.getUri();

            if (StringUtils.isBlank(uri)) {
                continue;
            }
            String abs_uri;

            // build an absolute URL
            try {
                URL tmpURL = new URL(url_, uri);
                abs_uri = tmpURL.toExternalForm();
            } catch (MalformedURLException e) {
                System.err.println("MalformedURLException:\n" + e.getMessage());
                continue;
            }

            links_out.add(abs_uri.toString());
        }

        return links_out;
    }

    private String getResponse(String uri) throws IOException {
        String url = "http://localhost:8080" + uri;

        URLConnection connection = new URL(url).openConnection();
        InputStream inputStream = connection.getInputStream();

        AutoDetectParser parser = new AutoDetectParser();
        LinkContentHandler link_handler = new LinkContentHandler();
        BodyContentHandler body_handler = new BodyContentHandler(10*1024*1024);
        // This causes weird crashes
        // LanguageHandler language_handler = new LanguageHandler();
        TeeContentHandler handler = new TeeContentHandler(link_handler, body_handler);
        Metadata metadata = new Metadata();

        System.out.println("Parsing: " + url);

        try {
            parser.parse(inputStream, handler, metadata);
        } catch (TikaException e) {
            System.err.println("Tika exception:\n" + e.getMessage());
            throw new IOException(e);
        } catch (SAXException e) {
            System.err.println("SAX exception:\n" + e.getMessage());
            throw new IOException(e);
        } finally {
            inputStream.close();
        }

        List<String> links = getAbsoluteLinks(url, link_handler.getLinks());

        /* Now return JSON with:
            {
                "language": language_handler.getLanguage(),
                "content": body_handler.toString(),
                "links": links,
                "metadata": metadata
            }
        */
        Gson gson = new Gson();
        JsonObject output_json = gson.toJsonTree(metadata).getAsJsonObject();
        output_json.add("content", gson.toJsonTree(body_handler.toString()));
        output_json.add("links", gson.toJsonTree(links));

        return output_json.toString();
    }
}
