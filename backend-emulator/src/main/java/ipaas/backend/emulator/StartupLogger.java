package ipaas.backend.emulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    @Value("${server.port:8888}")
    private int port;

    @Override
    public void run(ApplicationArguments args) {
        String url     = "http://localhost:" + port + "/backend/emulator";
        String history = "http://localhost:" + port + "/history";

        String msg = "\n"
            + "=".repeat(72) + "\n"
            + "  Backend Emulator Ready\n"
            + "=".repeat(72) + "\n"
            + "  Port    : " + port + "\n"
            + "  URL     : " + url + "\n"
            + "  History : " + history + "\n"
            + "-".repeat(72) + "\n"
            + "  [Port Change]\n"
            + "  1) Option  : java -jar backend-emulator-1.0.0.jar --server.port=9999\n"
            + "  2) Property: server.port=9999  (application.properties)\n"
            + "  3) Env     : export SERVER_PORT=9999\n"
            + "-".repeat(72) + "\n"
            + "  [Mode 1] Echo - respond with received headers/body as-is\n"
            + "\n"
            + "  curl -X POST " + url + " \\\n"
            + "    -H \"Content-Type: application/json\" \\\n"
            + "    -d '{\"hello\":\"world\"}'\n"
            + "-".repeat(72) + "\n"
            + "  [Mode 2] Controlled - custom response_code / headers / body\n"
            + "\n"
            + "  curl -X POST " + url + " \\\n"
            + "    -H \"Content-Type: application/json\" \\\n"
            + "    -d '{\n"
            + "          \"response_code\"        : 201,\n"
            + "          \"response_header\"      : \"RECORD_COUNT=100,RECORD_SIZE=200\",\n"
            + "          \"response_content_type\": \"application/json\",\n"
            + "          \"response_body\"        : \"{\\\"result\\\":\\\"ok\\\"}\",\n"
            + "          \"delay_time\"           : 1000\n"
            + "        }'\n"
            + "-".repeat(72) + "\n"
            + "  [Mode 3] Streaming - chunked response with delay\n"
            + "\n"
            + "  curl -X POST " + url + " --no-buffer \\\n"
            + "    -H \"Content-Type: application/json\" \\\n"
            + "    -d '{\n"
            + "          \"response_header\"      : \"RECORD_COUNT=100,RECORD_SIZE=200\",\n"
            + "          \"response_body\"        : \"aaaabbbccc\",\n"
            + "          \"stream\"               : true,\n"
            + "          \"chunk_size\"           : 3,\n"
            + "          \"delay_time\"           : 1000\n"
            + "        }'\n"
            + "-".repeat(72) + "\n"
            + "  [Mode 4] Error simulation - random error by error_rate (%)\n"
            + "\n"
            + "  curl -X POST " + url + " \\\n"
            + "    -H \"Content-Type: application/json\" \\\n"
            + "    -d '{\n"
            + "          \"response_body\" : \"ok\",\n"
            + "          \"error_rate\"    : 30\n"
            + "        }'\n"
            + "-".repeat(72) + "\n"
            + "  [History API]\n"
            + "  GET    " + history + "          - all history (max 100)\n"
            + "  GET    " + history + "?limit=10 - recent N requests\n"
            + "  DELETE " + history + "          - clear history\n"
            + "=".repeat(72) + "\n";

        log.info(msg);
    }
}
