package com.martinandersson.javaee.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Procedures for making {@code HTTP/1.1} request to the test server.<p>
 * 
 * All requests made with this class are non-persistent, meaning that the header
 * {@code Connection: close} is included in the request sent to the server.
 * After receiving this header, HTTP compliant web servers must close the
 * connection after the response.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public final class HttpRequests
{
    private HttpRequests() {
        // Empty
    }
    
    /**
     * Will make a GET-request to the provided Servlet test driver and return an
     * expected Java object as response.<p>
     * 
     * Do note that the underlying Java entity used to make the GET-request is
     * {@code HttpURLConnection} which most likely uses pooled connections.
     * Therefore, invoking this method in a concurrent test has limited effects.
     * Consider using a Socket instead (which we will add in this class in the
     * future, or change the implementation of this method).
     * 
     * @param <T> type of returned object
     * @param url deployed application URL ("application context root"),
     *            provided by Arquillian
     * @param testDriverType the test driver class
     * @param headers each header entry will be added to the GET-request
     * 
     * @return object returned by the test driver
     */
    public static <T> T getObject(URL url, Class<?> testDriverType, RequestParameter... headers) {
        final URL testDriver;
        final URLConnection conn;
        final String query = RequestParameter.buildQuery(headers);
        
        try {
            testDriver = new URL(url, testDriverType.getSimpleName() + query);
            conn = testDriver.openConnection();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        
        conn.setRequestProperty("Connection", "close");
        
        try (ObjectInputStream in = new ObjectInputStream(conn.getInputStream());) {
            return (T) in.readObject();
        }
        catch (IOException e) {
            // Might be that you haven't packaged all dependent class files with the @Deployment?
            // Servlet or endpoint your trying to call isn't properly implemented?
            throw new UncheckedIOException(e);
        }
        catch (ClassNotFoundException e) {
            throw new AssertionError("Got object of unknown type.", e);
        }
    }
    
    /**
     * Will make a POST-request to the provided Servlet test driver and return an
     * expected Java object as response.<p>
     * 
     * The POST body will contained the provided {@code toSend} object in his
     * binary form.<p>
     * 
     * This method is largely equivalent with {@linkplain
     * #getObject(URL, Class, RequestParameter...)}.
     * 
     * @param <T> type of returned object
     * @param url deployed application URL ("application context root"),
     *            provided by Arquillian
     * @param testDriverType the test driver class
     * @param toSend serialized and put in body of the POST request
     * 
     * @return object returned by the test driver
     */
    public static <T> T sendGetObject(URL url, Class<?> testDriverType, Serializable toSend) {
        Objects.requireNonNull(toSend);
        
        final URL testDriver; // = new URL(url, testDriverType.getSimpleName());
        final HttpURLConnection conn;
        
        try {
            testDriver = new URL(url, testDriverType.getSimpleName());
            conn = (HttpURLConnection) testDriver.openConnection();
            conn.setRequestMethod("POST");
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("Provided url is not a HTTP URI", e);
        }
        
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("Connection", "close");
        
        try (OutputStream raw = conn.getOutputStream();
             ObjectOutputStream writer = new ObjectOutputStream(raw);) {
             
             writer.writeObject(toSend);
             
             raw.write('\r'); raw.write('\n');
             raw.write('\r'); raw.write('\n');
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        
        try (InputStream raw = conn.getInputStream();
             ObjectInputStream reader = new ObjectInputStream(raw);) {
             return (T) reader.readObject();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (ClassNotFoundException e) {
            throw new AssertionError("Got object of unknown type.", e);
        }
    }
    
    
    
    public static class RequestParameter
    {
        private static String buildQuery(RequestParameter... requestParameters) {
            return Stream.of(requestParameters)
                    .map(RequestParameter::asKeyValue)
                    .collect(Collectors.joining("&", "?", ""));
        }
        
        private static final Pattern WS_CHAR = Pattern.compile("\\s");
        
        private final String key;
        private final String value;
        
        public RequestParameter(String key, String value) {
            if (WS_CHAR.matcher(key).find())
                throw new IllegalArgumentException("Whitespace found in key \"" + key + "\". Please percent-encode the key.");
            
            if (WS_CHAR.matcher(value).find())
                throw new IllegalArgumentException("Whitespace found in value: \"" + value + "\". Please percent-encode the value.");
            
            this.key = key;
            this.value = value;
        }
        
        public final String asKeyValue() {
            return key + "=" + value;
        }
    }
}