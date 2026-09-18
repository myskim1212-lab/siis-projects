package ipaas.backend.emulator.model;

import java.util.Map;

public class RequestLog {

    private int    no;
    private String timestamp;
    private String method;
    private String uri;
    private Map<String, String> requestHeaders;
    private String requestBody;
    private int    responseCode;
    private String mode;
    private String responseBody;

    public int    getNo()             { return no; }
    public String getTimestamp()      { return timestamp; }
    public String getMethod()         { return method; }
    public String getUri()            { return uri; }
    public Map<String, String> getRequestHeaders() { return requestHeaders; }
    public String getRequestBody()    { return requestBody; }
    public int    getResponseCode()   { return responseCode; }
    public String getMode()           { return mode; }
    public String getResponseBody()   { return responseBody; }

    public void setNo(int no)                               { this.no = no; }
    public void setTimestamp(String timestamp)               { this.timestamp = timestamp; }
    public void setMethod(String method)                     { this.method = method; }
    public void setUri(String uri)                           { this.uri = uri; }
    public void setRequestHeaders(Map<String, String> h)    { this.requestHeaders = h; }
    public void setRequestBody(String requestBody)           { this.requestBody = requestBody; }
    public void setResponseCode(int responseCode)            { this.responseCode = responseCode; }
    public void setMode(String mode)                         { this.mode = mode; }
    public void setResponseBody(String responseBody)         { this.responseBody = responseBody; }
}
