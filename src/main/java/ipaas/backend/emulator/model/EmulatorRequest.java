package ipaas.backend.emulator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmulatorRequest {

    @JsonProperty("response_code")
    private Integer responseCode;

    @JsonProperty("response_header")
    private String responseHeader;

    @JsonProperty("response_content_type")
    private String responseContentType;

    @JsonProperty("response_body")
    private String responseBody;

    @JsonProperty("stream")
    private Boolean stream;

    @JsonProperty("chunk_size")
    private Integer chunkSize;

    @JsonProperty("delay_time")
    private Integer delayTime;

    @JsonProperty("error_rate")
    private Integer errorRate;

    public Integer getResponseCode()       { return responseCode; }
    public String  getResponseHeader()     { return responseHeader; }
    public String  getResponseContentType(){ return responseContentType; }
    public String  getResponseBody()       { return responseBody; }
    public Boolean getStream()             { return stream; }
    public Integer getChunkSize()          { return chunkSize; }
    public Integer getDelayTime()          { return delayTime; }
    public Integer getErrorRate()          { return errorRate; }
}
