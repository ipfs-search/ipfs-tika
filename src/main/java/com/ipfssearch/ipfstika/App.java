package com.ipfssearch.ipfstika;

import java.io.InputStream;
import java.io.IOException;

import java.net.URL;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URLConnection;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

import fi.iki.elonen.NanoHTTPD;

import org.apache.commons.lang3.StringUtils;

import org.apache.http.client.utils.URIBuilder;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.Link;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.xml.sax.SAXException;

public class App extends NanoHTTPD {
    private URI _ipfs_gateway;
    private String _version;

    private String getVersion() {
        // Ref: https://stackoverflow.com/questions/2712970/get-maven-artifact-version-at-runtime

        String version = null;

        // try to load from maven properties first
        try {
            Properties p = new Properties();
            InputStream is = getClass().getResourceAsStream("/META-INF/maven/com.my.group/my-artefact/pom.properties");
            if (is != null) {
                p.load(is);
                version = p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        }

        // fallback to using Java API
        if (version == null) {
            Package aPackage = getClass().getPackage();
            if (aPackage != null) {
                version = aPackage.getImplementationVersion();
                if (version == null) {
                    version = aPackage.getSpecificationVersion();
                }
            }
        }

        if (version == null) {
            // we could not compute the version so use "dev"
            version = "dev-build";
        }

        return version;
    }

    public App(String hostname, int port, URI ipfs_gateway) throws IOException {
        super(hostname, port);

        // Get version
        _version = getVersion();

        // Store gateway on instance
        _ipfs_gateway = ipfs_gateway;

        System.out.println("IPFS gateway: "+_ipfs_gateway);

        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println(
            String.format(
                "\nipfs-tika %s accepting requests at: http://%s:%d/ \n",
                _version, hostname, port
            )
        );
    }

    private static String getEnv(String env_var, String def) {
        // Return setting from environment variable

        try {
            String setting = System.getenv(env_var);
            if (setting.length() > 1) {
                System.out.println(env_var+" read from environment.");
                return setting;
            }
        } catch (NullPointerException e) {
            // Env. variable not set, ignore
        } catch (Exception e) {
            Fatal("Error reading setting "+env_var+":\n" + e);
        }

        // Return default
        return def;
    }

    private static void Fatal(String msg) {
        // Exit with fatal error
        System.err.println("FATAL "+msg);
        System.exit(-1);
    }

    public static void main(String[] args) {
        String listen_host;
        int listen_port;
        URI ipfs_gateway;

        // Read settings from environment variable
        try {
            listen_host = getEnv("IPFS_TIKA_LISTEN_HOST", "localhost");
            listen_port = Integer.parseInt(getEnv("IPFS_TIKA_LISTEN_PORT", "8081"));
            ipfs_gateway = new URI(getEnv("IPFS_GATEWAY", "http://localhost:8080/"));
        } catch (Exception e) {
            Fatal("Error reading settings:\n" + e);
            return;
        }

        try {
            new App(listen_host, listen_port, ipfs_gateway);
        } catch (Exception e) {
            Fatal("Couldn't start server:\n" + e);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        try {
            return newFixedLengthResponse(getResponse(uri));
        } catch (IOException ioe) {
            System.err.println("Internal server error:\n" + ioe.toString());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, ioe.getMessage());
        }
    }

    private List<String> getAbsoluteLinks(URL parent_url, List<Link> links) {
        List<String> links_out = new ArrayList<String>();
        String uri;

        for (Link link : links) {
            uri = link.getUri();

            if (StringUtils.isBlank(uri)) {
                continue;
            }
            String abs_uri;

            // build an absolute URL
            try {
                URL tmpURL = new URL(parent_url, uri);
                abs_uri = tmpURL.toExternalForm();
            } catch (MalformedURLException e) {
                System.err.println("MalformedURLException:\n" + e.getMessage());
                continue;
            }

            links_out.add(abs_uri.toString());
        }

        return links_out;
    }

    private String getResponse(String path) throws IOException {
        // Generate properly escaped URL

        URI uri;
        URIBuilder uri_builder = new URIBuilder(_ipfs_gateway).setPath(path);


        try {
            uri = uri_builder.build();
        } catch (URISyntaxException e) {
            System.err.println("URI syntax exception:\n" + e.getMessage());
            throw new IOException(e);
        }

        System.out.println("Fetching: " + uri.toString());

        // Turn URL into input stream
        URL url = uri.toURL();

        // This will eliminate the need for a timeout on the crawler side and will improve reliability with larger
        // documents - as it actually won't timeout as long as data keeps coming.
        // https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(1000); // Should connect within 1s - this is real bad if it fails!
        connection.setReadTimeout(30*1000); // No data for 30s - die!

        InputStream inputStream = connection.getInputStream();
        TikaInputStream tikaInputStream = TikaInputStream.get(inputStream);

        AutoDetectParser parser = new AutoDetectParser();

        // Setup handler
        LinkContentHandler link_handler = new LinkContentHandler();
        BodyContentHandler body_handler = new BodyContentHandler(10*1024*1024);
        LanguageHandler language_handler = new LanguageHandler();
        TeeContentHandler handler = new TeeContentHandler(
            link_handler, body_handler, language_handler
        );

        Metadata metadata = new Metadata();

        // Set filename from path string
        String filename = path.substring(path.lastIndexOf("/")+1, path.length());
        metadata.set(Metadata.RESOURCE_NAME_KEY, filename);

        System.out.println("Parsing: " + uri.toString() + " ("+filename+")");

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
                "metadata": metadata,
                "ipfs_tika_version": _version,
            }
        */
        Gson gson = new Gson();
        JsonObject output_json = gson.toJsonTree(metadata).getAsJsonObject();
        output_json.add("content", gson.toJsonTree(body_handler.toString().trim()));
        output_json.add("language", gson.toJsonTree(language_handler.getLanguage()).getAsJsonObject());
        output_json.add("urls", gson.toJsonTree(links));
        output_json.add("ipfs_tika_version", gson.toJsonTree(_version));

        return output_json.toString();
    }
}
